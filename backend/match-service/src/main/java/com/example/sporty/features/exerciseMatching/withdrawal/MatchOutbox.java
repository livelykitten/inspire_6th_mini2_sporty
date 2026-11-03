package com.example.sporty.features.exerciseMatching.withdrawal;

import java.time.*;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;

@Entity
@Table(name = "match_outbox", indexes = @Index(name = "ix_match_outbox_status_id", columnList = "status,id"))
@Getter
@NoArgsConstructor
public class MatchOutbox {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "event_id", nullable = false, unique = true, length = 36)
    private String eventId;
    @Column(nullable = false, length = 100)
    private String topic;
    @Column(name = "message_key", nullable = false, length = 50)
    private String messageKey;
    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;
    @Column(nullable = false, length = 10) @ColumnDefault("'PENDING'")
    private String status = "PENDING";
    @Column(name = "attempt_count", nullable = false) @ColumnDefault("0")
    private int attemptCount;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "sent_at", columnDefinition = "DATETIME")
    private LocalDateTime sentAt;

    public MatchOutbox(String eventId, String topic, Long userId, String payload) {
        this.eventId = eventId;
        this.topic = topic;
        this.messageKey = userId.toString();
        this.payload = payload;
        this.createdAt = Instant.now();
    }

    public void sent() {
        status = "SENT";
        sentAt = LocalDateTime.now(ZoneId.of("Asia/Seoul")).withNano(0);
    }
    public void failedAttempt() { attemptCount++; }
}
