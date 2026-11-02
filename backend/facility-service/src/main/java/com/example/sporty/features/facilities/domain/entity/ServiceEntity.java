package com.example.sporty.features.facilities.domain.entity;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/*
서울시 API에서 가져온 개별 체육시설 예약 서비스다.
*/
@Entity
@Table(name = "service")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ServiceEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "location_id", nullable = false)
    private LocationEntity location;

    @Column(name = "service_id", length = 50, nullable = false, unique = true)
    private String serviceId;

    @Column(length = 200, nullable = false)
    private String name;

    @Column(length = 20)
    private String status;

    private LocalTime startTime;
    private LocalTime endTime;

    @Column(name = "is_free")
    private Boolean isFree;

    @Column(length = 200)
    private String url;

    @Column(name = "reservation_deadline_at")
    private LocalDateTime reservationDeadlineAt;

    @Column(nullable = false)
    private boolean active = true;

    @CreationTimestamp
    @Column(
        name = "created_at",
        nullable = false,
        updatable = false,
        columnDefinition = "TIMESTAMP"
    )
    private Instant createdAt;

    @UpdateTimestamp
    @Column(
        name = "updated_at",
        nullable = false,
        columnDefinition = "TIMESTAMP"
    )
    private Instant updatedAt;

    @Builder
    public ServiceEntity(
            LocationEntity location,
            String serviceId,
            String name,
            String status,
            LocalTime startTime,
            LocalTime endTime,
            Boolean isFree,
            String url,
            LocalDateTime reservationDeadlineAt
    ) {
        this.location = location;
        this.serviceId = serviceId;
        this.name = name;
        this.status = status;
        this.startTime = startTime;
        this.endTime = endTime;
        this.isFree = isFree;
        this.url = url;
        this.reservationDeadlineAt = reservationDeadlineAt;
        this.active = true;
    }

    public void update(
            LocationEntity location,
            String name,
            String status,
            LocalTime startTime,
            LocalTime endTime,
            Boolean isFree,
            String url,
            LocalDateTime reservationDeadlineAt
    ) {
        this.location = location;
        this.name = name;
        this.status = status;
        this.startTime = startTime;
        this.endTime = endTime;
        this.isFree = isFree;
        this.url = url;
        this.reservationDeadlineAt = reservationDeadlineAt;
        this.active = true;
    }

    public void deactivate() {
        this.active = false;
    }
}
