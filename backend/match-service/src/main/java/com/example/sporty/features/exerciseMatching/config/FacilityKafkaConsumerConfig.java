package com.example.sporty.features.exerciseMatching.config;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.RetryListener;
import org.springframework.util.backoff.FixedBackOff;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Configuration
public class FacilityKafkaConsumerConfig {

    private static final long RETRY_INTERVAL_MILLIS = 1_000L;
    private static final long RETRY_COUNT = 3L;

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> facilityKafkaListenerContainerFactory(
            ConsumerFactory<String, String> consumerFactory,
            KafkaTemplate<String, String> kafkaTemplate
    ) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                kafkaTemplate,
                (record, exception) -> {
                    String dltTopic = record.topic() + ".DLT";
                    log.error("[KAFKA] DLT. sourceTopic={}, dltTopic={}, partition={}, offset={}, error={}",
                            record.topic(), dltTopic, record.partition(), record.offset(),
                            exception.getClass().getSimpleName());
                    return new TopicPartition(dltTopic, record.partition());
                });

        DefaultErrorHandler errorHandler = new DefaultErrorHandler(
                recoverer,
                new FixedBackOff(RETRY_INTERVAL_MILLIS, RETRY_COUNT));
        errorHandler.setRetryListeners(new RetryListener() {
            @Override
            public void failedDelivery(
                    ConsumerRecord<?, ?> record,
                    Exception exception,
                    int deliveryAttempt
            ) {
                if (deliveryAttempt <= RETRY_COUNT) {
                    log.warn("[KAFKA] retry {}/{}. topic={}, partition={}, offset={}, error={}",
                            deliveryAttempt, RETRY_COUNT,
                            record.topic(), record.partition(), record.offset(),
                            exception.getClass().getSimpleName());
                }
            }
        });

        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.setCommonErrorHandler(errorHandler);
        return factory;
    }
}
