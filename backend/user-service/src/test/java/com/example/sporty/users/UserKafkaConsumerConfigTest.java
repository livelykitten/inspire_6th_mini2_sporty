package com.example.sporty.users;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.kafka.support.SendResult;

import com.example.sporty.features.commons.config.UserKafkaConsumerConfig;

/** 탈퇴 Saga 설계 §4.2·§9: 수신 실패는 팀 명세의 {원본토픽}.DLT로 보내고, DLT 전송이 실패하면 복구로 보지 않는다. */
class UserKafkaConsumerConfigTest {
    // EV-03 수신 토픽. 소비자 장애는 이 토픽의 DLT로 이관된다.
    private static final String TOPIC = "match.withdrawal.completed";

    @SuppressWarnings("unchecked")
    private final KafkaTemplate<String, String> template = mock(KafkaTemplate.class);
    @SuppressWarnings("unchecked")
    private final ProducerFactory<String, String> producers = mock(ProducerFactory.class);
    private final Consumer<?, ?> consumer = mock(Consumer.class);
    private final MessageListenerContainer container = mock(MessageListenerContainer.class);
    private DefaultErrorHandler handler;

    @BeforeEach
    void setUp() {
        when(template.getProducerFactory()).thenReturn(producers);
        when(producers.getConfigurationProperties()).thenReturn(Map.of());
        handler = new UserKafkaConsumerConfig().userKafkaErrorHandler(template);
    }

    @Test
    @DisplayName("최초 처리 1회와 1초 간격 재시도 3회가 모두 실패한 뒤에만 {원본토픽}.DLT로 보낸다")
    @SuppressWarnings({"unchecked", "rawtypes"})
    void routesToDltOnlyAfterRetriesAreExhausted() {
        when(template.send(any(ProducerRecord.class)))
                .thenReturn(CompletableFuture.completedFuture((SendResult<String, String>) mock(SendResult.class)));
        ConsumerRecord<String, String> record = new ConsumerRecord<>(TOPIC, 2, 15L, "7", "{}");
        RuntimeException failure = new IllegalStateException("db down");

        // 최초 처리 1회 + 1초 간격 재시도 3회 = 총 4회 전달된다. 앞의 3번 실패는 재전달 대상으로 남긴다.
        for (int attempt = 1; attempt <= 3; attempt++) {
            assertThat(handler.handleOne(failure, record, consumer, container)).isFalse();
            verify(template, never()).send(any(ProducerRecord.class));
        }

        // 4번째 실패로 재시도가 소진되면 DLT로 이관한다.
        assertThat(handler.handleOne(failure, record, consumer, container)).isTrue();

        ArgumentCaptor<ProducerRecord<String, String>> sent = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(template).send(sent.capture());
        assertThat(sent.getValue().topic()).isEqualTo(TOPIC + ".DLT");
        assertThat(sent.getValue().value()).isEqualTo("{}");
    }

    @Test
    @DisplayName("DLT 전송이 실패하면 복구 완료로 처리하지 않아 원본 오프셋을 넘기지 않는다")
    @SuppressWarnings({"unchecked", "rawtypes"})
    void dltSendFailureIsNotTreatedAsRecovered() {
        when(template.send(any(ProducerRecord.class)))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("broker down")));
        ConsumerRecord<String, String> record = new ConsumerRecord<>(TOPIC, 0, 3L, "7", "{}");
        RuntimeException failure = new IllegalStateException("db down");
        for (int attempt = 1; attempt <= 3; attempt++) {
            handler.handleOne(failure, record, consumer, container);
        }

        // 4번째 실패에서 DLT 전송을 시도하지만 실패하므로 복구 완료(true)가 되면 안 된다.
        boolean recovered;
        try {
            recovered = handler.handleOne(failure, record, consumer, container);
        } catch (RuntimeException dltFailure) {
            recovered = false;
        }

        verify(template).send(any(ProducerRecord.class));
        assertThat(recovered).isFalse();
    }
}
