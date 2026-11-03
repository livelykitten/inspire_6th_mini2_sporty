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

    @KafkaListener(
            topics = "facility.service.changed",
            groupId = "match-service",
            containerFactory = "facilityKafkaListenerContainerFactory")
    public void consume(String payload) {
        try {
            FacilityServiceChangedEvent event = objectMapper.readValue(
                    payload,
                    FacilityServiceChangedEvent.class);
            validateRequiredFields(event);

            log.info("[KAFKA] facility event received. eventId={}, serviceId={}",
                    event.eventId(), event.serviceId());
            facilitySnapshotService.apply(event);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Invalid facility.service.changed payload", exception);
        }
    }

    private void validateRequiredFields(FacilityServiceChangedEvent event) {
        if (event.eventId() == null || event.eventId().isBlank()
                || event.occurredAt() == null
                || event.serviceId() == null
                || event.name() == null || event.name().isBlank()
                || event.locationName() == null || event.locationName().isBlank()
                || event.region() == null || event.region().isBlank()
                || event.active() == null) {
            throw new IllegalArgumentException("Missing required facility.service.changed field");
        }
    }
}
