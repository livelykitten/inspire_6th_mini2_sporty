package com.example.sporty.features.exerciseMatching.withdrawal;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.*;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "saga.outbox.enabled", havingValue = "true", matchIfMissing = true)
public class MatchOutboxPublisher {
    private final MatchOutboxSender sender;

    @Scheduled(fixedDelayString = "${saga.outbox.publish-delay-ms:1000}")
    public void publish() {
        for (int i = 0; i < 100 && sender.publishNext(); i++) { /* bounded batch, id order */ }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableScheduling
    static class Scheduling {}
}
