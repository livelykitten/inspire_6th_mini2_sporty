package com.example.sporty.features.exerciseMatching.event;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.sporty.features.exerciseMatching.service.FacilitySnapshotService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class FacilityServiceChangedListener {

    private final ObjectMapper objectMapper;
    private final FacilitySnapshotService facilitySnapshotService;

    @KafkaListener(topics = "facility.service.changed", groupId = "match-service")
    public void consume(String payload) {
        try {
            FacilityServiceChangedEvent event = objectMapper.readValue(
                    payload,
                    FacilityServiceChangedEvent.class);

            log.info("[KAFKA] facility event received. eventId={}, serviceId={}",
                    event.eventId(), event.serviceId());
            facilitySnapshotService.apply(event);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Invalid facility.service.changed payload", exception);
        }
    }
}
