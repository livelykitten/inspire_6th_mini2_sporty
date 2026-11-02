package com.example.sporty.features.facilities.domain.entity;

import java.math.BigDecimal;
import java.time.Instant;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/*
 체육시설 서비스가 열리는 장소와 시설 소분류 정보다.
 동일 장소·시설 종류의 여러 예약 서비스가 이 엔티티를 함께 참조한다.
 */
@Entity
@Table(name = "location")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LocationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 20, nullable = false)
    private String region;

    @Column(length = 100, nullable = false)
    private String name;

    @Column(name = "facility_name", length = 100)
    private String facilityName;

    @Column(length = 100)
    private String contact;

    @Column(precision = 18, scale = 15)
    private BigDecimal latitude;

    @Column(precision = 18, scale = 15)
    private BigDecimal longitude;

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
    public LocationEntity(
            String region,
            String name,
            String facilityName,
            String contact,
            BigDecimal latitude,
            BigDecimal longitude
    ) {
        this.region = region;
        this.name = name;
        this.facilityName = facilityName;
        this.contact = contact;
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public void update(
            String facilityName,
            String contact,
            BigDecimal latitude,
            BigDecimal longitude
    ) {
        this.facilityName = facilityName;
        this.contact = contact;
        this.latitude = latitude;
        this.longitude = longitude;
    }
}
