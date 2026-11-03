package com.example.sporty.features.exerciseMatching.withdrawal;

import java.time.Instant;
import jakarta.persistence.*;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "processed_event")
@NoArgsConstructor
public class ProcessedEvent {
    @Id
    @Column(name = "event_id", length = 36, nullable = false)
    private String eventId;
    @Column(nullable = false, length = 100)
    private String topic;
    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;

    public ProcessedEvent(String eventId, String topic) {
        this.eventId = eventId;
        this.topic = topic;
        this.processedAt = Instant.now();
    }
}
