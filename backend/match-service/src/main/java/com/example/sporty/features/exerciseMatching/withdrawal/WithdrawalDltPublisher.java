package com.example.sporty.features.exerciseMatching.withdrawal;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.springframework.kafka.KafkaException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class WithdrawalDltPublisher {
    private final KafkaTemplate<String, String> kafka;

    public void publish(ConsumerRecord<?, ?> original, Exception cause) {
        // EV-02 factory uses StringDeserializer: preserve even malformed JSON verbatim.
        var headers = new RecordHeaders(original.headers().toArray());
        headers.add(KafkaHeaders.DLT_ORIGINAL_TOPIC, original.topic().getBytes(StandardCharsets.UTF_8));
        headers.add(KafkaHeaders.DLT_ORIGINAL_PARTITION, ByteBuffer.allocate(4).putInt(original.partition()).array());
        headers.add(KafkaHeaders.DLT_ORIGINAL_OFFSET, ByteBuffer.allocate(8).putLong(original.offset()).array());
        headers.add(KafkaHeaders.DLT_EXCEPTION_FQCN, cause.getClass().getName().getBytes(StandardCharsets.UTF_8));
        var record = new ProducerRecord<>(original.topic() + ".DLT", original.partition(),
                original.timestamp() < 0 ? null : original.timestamp(),
                (String) original.key(), (String) original.value(), headers);
        try {
            kafka.send(record).get(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            throw new KafkaException("Withdrawal DLT publication unconfirmed; retain original offset", e);
        }
        log.warn("[KAFKA] withdrawal preserved in DLT topic={} partition={} originalOffset={}",
                record.topic(), record.partition(), original.offset());
    }
}
