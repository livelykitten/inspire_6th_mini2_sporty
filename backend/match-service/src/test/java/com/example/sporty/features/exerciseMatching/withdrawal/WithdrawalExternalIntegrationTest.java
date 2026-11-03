package com.example.sporty.features.exerciseMatching.withdrawal;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;
import org.apache.kafka.clients.admin.*;
import org.apache.kafka.clients.consumer.*;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.kafka.core.*;
import org.springframework.kafka.listener.*;
import org.springframework.kafka.config.MethodKafkaListenerEndpoint;
import org.springframework.messaging.handler.annotation.support.DefaultMessageHandlerMethodFactory;
import org.springframework.kafka.support.Acknowledgment;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** Opt-in only: a dedicated disposable MariaDB schema and uniquely named Kafka topics. */
@Tag("external")
@EnabledIfEnvironmentVariable(named = "W32_EXTERNAL_TESTS", matches = "true")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class WithdrawalExternalIntegrationTest extends WithdrawalFailureIntegrationTest {
    @DynamicPropertySource
    static void database(DynamicPropertyRegistry properties) {
        String url = Objects.requireNonNull(System.getenv("W32_JDBC_URL"), "W32_JDBC_URL required");
        if (!url.matches("jdbc:mariadb://(?:localhost|127\\.0\\.0\\.1):[0-9]+/w32_test_[a-zA-Z0-9_]+")) {
            throw new IllegalArgumentException("Only an isolated local w32_test_* schema may be recreated");
        }
        properties.add("spring.datasource.url", () -> url);
        properties.add("spring.datasource.username", () -> System.getenv("W32_JDBC_USER"));
        properties.add("spring.datasource.password", () -> System.getenv("W32_JDBC_PASSWORD"));
        properties.add("spring.datasource.driver-class-name", () -> "org.mariadb.jdbc.Driver");
        properties.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.MariaDBDialect");
        properties.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        properties.add("spring.sql.init.mode", () -> "never");
    }

    @Test
    void realKafkaRetriesFourTimesThenCommitsOffsetAfterMariaDbCompensation() throws Exception {
        runKafka(false);
    }

    @Test
    void realKafkaLookupFailureGoesToDltWithoutCompensation() throws Exception {
        runKafka(true);
    }

    @Test
    void malformedJsonIsNotRetriedThroughRealListenerAdapter() throws Exception {
        runInvalidInput("{\"eventId\":", JsonProcessingException.class, 1);
    }

    @Test
    void validJsonMissingRequiredFieldsKeepsExistingFourAttemptPolicy() throws Exception {
        runInvalidInput("{\"eventId\":\"" + UUID.randomUUID() + "\"}", IllegalArgumentException.class, 4);
    }

    private void runInvalidInput(String payload, Class<? extends Throwable> expectedCause, int expectedAttempts)
            throws Exception {
        String broker = System.getenv().getOrDefault("W32_KAFKA_BOOTSTRAP", "localhost:9092");
        String topic = "w32-test-" + UUID.randomUUID() + ".requested";
        String group = "w32-test-" + UUID.randomUUID();
        var pf = new DefaultKafkaProducerFactory<String, String>(Map.of("bootstrap.servers", broker,
                "key.serializer", StringSerializer.class, "value.serializer", StringSerializer.class, "acks", "all"));
        var realKafka = new KafkaTemplate<>(pf);
        var cf = new DefaultKafkaConsumerFactory<String, String>(Map.of("bootstrap.servers", broker,
                "key.deserializer", StringDeserializer.class, "value.deserializer", StringDeserializer.class,
                "auto.offset.reset", "earliest", "enable.auto.commit", false));
        var attempts = new AtomicInteger();
        var deliveryAttempts = new CopyOnWriteArrayList<Integer>();
        var exceptions = new CopyOnWriteArrayList<Exception>();
        var dltConfirmed = new AtomicBoolean();
        var offsetBeforeDlt = new AtomicReference<Long>();
        var actualListener = new WithdrawalRequestedListener(mapper, service) {
            @Override
            public void consume(String body, Acknowledgment ack) throws JsonProcessingException {
                attempts.incrementAndGet();
                super.consume(body, ack);
            }
        };
        try (var admin = Admin.create(Map.of("bootstrap.servers", broker))) {
            var dltPublisher = new WithdrawalDltPublisher(realKafka) {
                @Override
                public void publish(ConsumerRecord<?, ?> original, Exception cause) {
                    try { offsetBeforeDlt.set(committedOffset(admin, group, topic)); }
                    catch (Exception e) { throw new IllegalStateException("Cannot inspect source offset", e); }
                    super.publish(original, cause); // Real send + broker acknowledgment, no mock/bypass.
                    dltConfirmed.set(true);
                }
            };
            var recovery = new WithdrawalExhaustedRecoverer(mapper, users, failures, dltPublisher);
            var configuration = new WithdrawalKafkaConfiguration();
            var factory = configuration.withdrawalKafkaListenerContainerFactory(cf, recovery);
            var methodFactory = new DefaultMessageHandlerMethodFactory();
            methodFactory.afterPropertiesSet();
            var endpoint = new MethodKafkaListenerEndpoint<String, String>();
            endpoint.setId(group);
            endpoint.setGroupId(group);
            endpoint.setTopics(topic);
            endpoint.setBean(actualListener);
            endpoint.setMethod(WithdrawalRequestedListener.class.getMethod("consume", String.class, Acknowledgment.class));
            endpoint.setMessageHandlerMethodFactory(methodFactory);
            var container = factory.createListenerContainer(endpoint);
            assertThat(container.getCommonErrorHandler()).isInstanceOf(DefaultErrorHandler.class);
            var handler = (DefaultErrorHandler) container.getCommonErrorHandler();
            handler.setRetryListeners((record, exception, attempt) -> {
                exceptions.add(exception);
                deliveryAttempts.add(attempt); // Observe the actual factory-installed handler.
            });
            container.getContainerProperties().setPollTimeout(100);
            admin.createTopics(List.of(new NewTopic(topic, 1, (short) 1), new NewTopic(topic + ".DLT", 1, (short) 1)))
                    .all().get(10, TimeUnit.SECONDS);
            try {
                container.start();
                await(() -> container.getAssignedPartitions() != null && !container.getAssignedPartitions().isEmpty());
                var sent = realKafka.send(topic, "invalid-json-key", payload).get(10, TimeUnit.SECONDS);
                long expectedOffset = sent.getRecordMetadata().offset() + 1;
                await(() -> {
                    try { return Long.valueOf(expectedOffset).equals(committedOffset(admin, group, topic)); }
                    catch (Exception e) { return false; }
                });
                container.stop();
                assertThat(attempts.get()).as("actual listener invocations").isEqualTo(expectedAttempts);
                assertThat(deliveryAttempts).containsExactlyElementsOf(
                        java.util.stream.IntStream.rangeClosed(1, expectedAttempts).boxed().toList());
                assertThat(exceptions).hasSize(expectedAttempts);
                assertThat(exceptions.get(0)).isInstanceOf(ListenerExecutionFailedException.class);
                var chain = new ArrayList<Class<?>>();
                for (Throwable e = exceptions.get(0); e != null; e = e.getCause()) chain.add(e.getClass());
                assertThat(chain).anyMatch(expectedCause::isAssignableFrom);
                if (expectedCause == IllegalArgumentException.class) {
                    assertThat(chain).noneMatch(JsonProcessingException.class::isAssignableFrom);
                }
                assertThat(offsetBeforeDlt.get()).isIn(null, 0L);
                assertThat(dltConfirmed.get()).as("DLT future completed before source offset commit").isTrue();
                try (var consumer = cf.createConsumer(group + "-dlt", "", "")) {
                    consumer.subscribe(List.of(topic + ".DLT"));
                    var records = new ArrayList<ConsumerRecord<String, String>>();
                    await(() -> {
                        consumer.poll(Duration.ofMillis(200)).forEach(records::add);
                        return !records.isEmpty();
                    });
                    assertThat(records).hasSize(1);
                    assertThat(records.get(0).key()).isEqualTo("invalid-json-key");
                    assertThat(records.get(0).value()).isEqualTo(payload);
                }
                assertThat(outbox.count()).isZero();
                assertThat(processed.count()).isZero();
                assertRestored();
                verifyNoInteractions(users); // Rejected before IN-01 and cleanup/compensation.
                System.out.printf("W32 classification causeChain=%s listenerAttempts=%d extraRetries=%d "
                                + "failedDeliveryAttempts=%s dltKeyValue=identical offsetBeforeDlt=%s offsetAfterDlt=%d%n",
                        chain.stream().map(Class::getName).toList(), attempts.get(), attempts.get() - 1,
                        deliveryAttempts, offsetBeforeDlt.get(), expectedOffset);
            } finally {
                container.stop(); realKafka.destroy(); pf.destroy();
                admin.deleteTopics(List.of(topic, topic + ".DLT")).all().get(10, TimeUnit.SECONDS);
            }
        }
    }

    private static Long committedOffset(Admin admin, String group, String topic) throws Exception {
        var offset = admin.listConsumerGroupOffsets(group).partitionsToOffsetAndMetadata()
                .get(5, TimeUnit.SECONDS).get(new TopicPartition(topic, 0));
        return offset == null ? null : offset.offset();
    }

    private void runKafka(boolean lookupFailure) throws Exception {
        String broker = System.getenv().getOrDefault("W32_KAFKA_BOOTSTRAP", "localhost:9092");
        String topic = "w32-test-" + UUID.randomUUID() + ".requested";
        String group = "w32-test-" + UUID.randomUUID();
        var producerFactory = new DefaultKafkaProducerFactory<String, String>(Map.of(
                "bootstrap.servers", broker, "key.serializer", StringSerializer.class,
                "value.serializer", StringSerializer.class, "acks", "all"));
        var realKafka = new KafkaTemplate<>(producerFactory);
        var cf = new DefaultKafkaConsumerFactory<String, String>(Map.of("bootstrap.servers", broker,
                "key.deserializer", StringDeserializer.class, "value.deserializer", StringDeserializer.class,
                "auto.offset.reset", "earliest", "enable.auto.commit", false));
        var realRecoverer = new WithdrawalExhaustedRecoverer(mapper, users, failures, new WithdrawalDltPublisher(realKafka));
        var factory = new WithdrawalKafkaConfiguration().withdrawalKafkaListenerContainerFactory(cf, realRecoverer);
        var container = factory.createContainer(topic);
        container.setBeanName(group);
        container.getContainerProperties().setGroupId(group);
        container.getContainerProperties().setPollTimeout(100);
        var attempts = new AtomicInteger();
        var listener = new WithdrawalRequestedListener(mapper, service);
        container.getContainerProperties().setMessageListener((AcknowledgingMessageListener<String, String>) (record, ack) -> {
            attempts.incrementAndGet();
            try { listener.consume(record.value(), ack); }
            catch (com.fasterxml.jackson.core.JsonProcessingException e) { throw new IllegalArgumentException(e); }
        });
        if (lookupFailure) when(users.getUserStatus(7L)).thenThrow(new IllegalStateException("Feign outage"));
        try (var admin = Admin.create(Map.of("bootstrap.servers", broker))) {
            admin.createTopics(List.of(new NewTopic(topic, 1, (short) 1), new NewTopic(topic + ".DLT", 1, (short) 1)))
                    .all().get(10, TimeUnit.SECONDS);
            try {
                container.start();
                await(() -> container.getAssignedPartitions() != null && !container.getAssignedPartitions().isEmpty());
                String payload = mapper.writeValueAsString(event);
                realKafka.send(topic, "7", payload).get(10, TimeUnit.SECONDS);
                await(() -> {
                    try {
                        var offset = admin.listConsumerGroupOffsets(group).partitionsToOffsetAndMetadata()
                                .get(5, TimeUnit.SECONDS).get(new TopicPartition(topic, 0));
                        return offset != null && offset.offset() == 1;
                    } catch (Exception e) { return false; }
                });
                assertThat(attempts.get()).isEqualTo(4);
                assertRestored();
                if (lookupFailure) {
                    assertThat(outbox.count()).isZero();
                    assertThat(processed.count()).isZero();
                    try (var consumer = cf.createConsumer(group + "-dlt", "", "")) {
                        consumer.subscribe(List.of(topic + ".DLT"));
                        var received = new ArrayList<ConsumerRecord<String, String>>();
                        await(() -> {
                            consumer.poll(Duration.ofMillis(200)).forEach(received::add);
                            return !received.isEmpty();
                        });
                        assertThat(received.get(0).key()).isEqualTo("7");
                        assertThat(received.get(0).value()).isEqualTo(payload);
                    }
                } else assertCompensated();
                System.out.printf("W32 external topic=%s attempts=%d offset=1 result=%s%n",
                        topic, attempts.get(), lookupFailure ? "DLT" : "EV-04 PENDING");
            } finally {
                container.stop();
                realKafka.destroy();
                producerFactory.destroy();
                admin.deleteTopics(List.of(topic, topic + ".DLT")).all().get(10, TimeUnit.SECONDS);
            }
        }
    }

    private static void await(BooleanSupplier ready) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(25).toNanos();
        while (!ready.getAsBoolean()) {
            if (System.nanoTime() > deadline) throw new AssertionError("External integration condition timed out");
            Thread.sleep(100);
        }
    }
}
