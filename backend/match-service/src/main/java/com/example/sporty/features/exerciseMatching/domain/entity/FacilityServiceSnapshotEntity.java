package com.example.sporty.features.exerciseMatching.domain.entity;

import java.time.LocalDateTime;

import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "facility_service_snapshot")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FacilityServiceSnapshotEntity {

    @Id
    @Column(name = "service_id")
    private Long serviceId;

    @Column(name = "name", length = 200, nullable = false)
    private String name;

    @Column(name = "location_name", length = 100, nullable = false)
    private String locationName;

    @Column(name = "region", length = 20, nullable = false)
    private String region;

    @Column(name = "is_free", columnDefinition = "BIT(1)")
    private Boolean isFree;

    @Column(name = "reservation_deadline_at", columnDefinition = "DATETIME")
    private LocalDateTime reservationDeadlineAt;

    @Column(name = "active", nullable = false, columnDefinition = "BIT(1)")
    private Boolean active;

    @Column(name = "source_updated_at", nullable = false, columnDefinition = "DATETIME")
    private LocalDateTime sourceUpdatedAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false, columnDefinition = "TIMESTAMP")
    private LocalDateTime updatedAt;

    public void updateSnapshot(
            String name,
            String locationName,
            String region,
            Boolean isFree,
            LocalDateTime reservationDeadlineAt,
            Boolean active,
            LocalDateTime sourceUpdatedAt
    ) {
        this.name = name;
        this.locationName = locationName;
        this.region = region;
        this.isFree = isFree;
        this.reservationDeadlineAt = reservationDeadlineAt;
        this.active = active;
        this.sourceUpdatedAt = sourceUpdatedAt;
    }
}
