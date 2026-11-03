package com.example.sporty.features.exerciseMatching.event;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.example.sporty.features.exerciseMatching.service.FacilitySnapshotService;

@ExtendWith(MockitoExtension.class)
class FacilityServiceChangedListenerTest {

    @Mock
    private FacilitySnapshotService facilitySnapshotService;

    private FacilityServiceChangedListener listener;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        listener = new FacilityServiceChangedListener(objectMapper, facilitySnapshotService);
    }

    @Test
    void deserializesEv01AndAppliesSnapshot() {
        String payload = """
                {
                  "eventId": "event-1",
                  "occurredAt": "2026-11-03T10:30:00",
                  "serviceId": 101,
                  "name": "테스트 풋살장",
                  "locationName": "테스트 체육시설",
                  "region": "강남구",
                  "isFree": true,
                  "reservationDeadlineAt": null,
                  "active": false
                }
                """;

        listener.consume(payload);

        verify(facilitySnapshotService).apply(argThat(event ->
                event.serviceId().equals(101L)
                        && event.name().equals("테스트 풋살장")
                        && Boolean.FALSE.equals(event.active())));
    }

    @Test
    void rejectsMalformedPayloadSoKafkaErrorHandlingCanProcessIt() {
        assertThatThrownBy(() -> listener.consume("not-json"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid facility.service.changed payload");
    }

    @Test
    void rejectsPayloadWithMissingRequiredFieldSoKafkaErrorHandlingCanProcessIt() {
        String payload = """
                {
                  "eventId": "event-1",
                  "occurredAt": null,
                  "serviceId": 101,
                  "name": "테스트 풋살장",
                  "locationName": "테스트 체육시설",
                  "region": "강남구",
                  "isFree": true,
                  "reservationDeadlineAt": null,
                  "active": true
                }
                """;

        assertThatThrownBy(() -> listener.consume(payload))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Missing required facility.service.changed field");
    }
}
