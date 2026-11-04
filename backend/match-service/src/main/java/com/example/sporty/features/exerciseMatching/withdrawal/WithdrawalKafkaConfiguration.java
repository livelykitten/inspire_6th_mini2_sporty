package com.example.sporty.features.exerciseMatching.withdrawal;

import java.util.Map;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.context.annotation.*;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.*;
import org.springframework.kafka.listener.*;
import org.springframework.util.backoff.FixedBackOff;

@Configuration(proxyBeanMethods = false)
@lombok.extern.slf4j.Slf4j
public class WithdrawalKafkaConfiguration {
    @Bean
    ConcurrentKafkaListenerContainerFactory<String, String> withdrawalKafkaListenerContainerFactory(
            ConsumerFactory<?, ?> consumerFactory, WithdrawalExhaustedRecoverer recoverer) {
        // Isolate EV-02 ack/recovery policy from the existing facility listener.
        var properties = new java.util.HashMap<String, Object>(consumerFactory.getConfigurationProperties());
        properties.putIfAbsent(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        properties.putAll(Map.of(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false,
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class));
        var factory = new ConcurrentKafkaListenerContainerFactory<String, String>();
        factory.setConsumerFactory(new DefaultKafkaConsumerFactory<>(properties));
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        factory.getContainerProperties().setSyncCommits(true);
        factory.setCommonErrorHandler(withdrawalErrorHandler(recoverer));
        return factory;
    }

    DefaultErrorHandler withdrawalErrorHandler(WithdrawalExhaustedRecoverer recoverer) {
        var handler = new DefaultErrorHandler(recoverer, new FixedBackOff(1000L, 3L));
        handler.setAckAfterHandle(false);
        // SeekUtils commits recovered offset+1 only after recoverer returns normally.
        handler.setCommitRecovered(true);
        handler.addNotRetryableExceptions(JsonProcessingException.class, WithdrawalCommitUncertainException.class);
        handler.setRetryListeners((record, exception, attempt) ->
                log.warn("[KAFKA] withdrawal retry topic={} partition={} offset={} attempt={}",
                        record.topic(), record.partition(), record.offset(), attempt));
        return handler;
    }
}
