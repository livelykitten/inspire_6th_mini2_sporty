package com.example.sporty.features.users.domain.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "user_outbox",
        indexes = @Index(
                name = "idx_user_outbox_status_id",
                columnList = "status,id"
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserOutbox {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", length = 36, nullable = false, unique = true)
    private String eventId;

    @Column(length = 100, nullable = false)
    private String topic;

    @Column(name = "message_key", length = 50, nullable = false)
    private String messageKey;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String payload;

    @Column(length = 10, nullable = false)
    private String status = "PENDING";

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount = 0;

    @CreationTimestamp
    @Column(
            name = "created_at",
            nullable = false,
            updatable = false,
            columnDefinition = "TIMESTAMP"
    )
    private LocalDateTime createdAt;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    public static UserOutbox withdrawalRequested(
            String eventId,
            Long userId,
            String payload
    ) {
        UserOutbox outbox = new UserOutbox();
        outbox.eventId = eventId;
        outbox.topic = "user.withdrawal.requested";
        outbox.messageKey = userId.toString();
        outbox.payload = payload;
        return outbox;
    }

    public void markSent() {
        this.status = "SENT";
        this.sentAt = LocalDateTime.now();
    }

    public void recordFailure() {
        this.attemptCount++;
    }
}