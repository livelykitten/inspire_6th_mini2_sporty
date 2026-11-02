package com.example.sporty.features.exerciseMatching.domain.entity;

import java.time.Instant;
import java.time.LocalDateTime;

import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.Check;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import com.example.sporty.features.commons.util.SportType;
import com.example.sporty.features.exerciseMatching.domain.enums.GenderGroup;
import com.example.sporty.features.exerciseMatching.domain.enums.MatchStatus;
import com.example.sporty.features.exerciseMatching.domain.enums.SkillLevel;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ForeignKey;
import com.example.sporty.features.facilities.domain.entity.ServiceEntity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Entity
@Table(name = "`match`")
@Check(name = "ck_match_valid_fields", constraints = "CHAR_LENGTH(TRIM(title)) > 0 AND max_participant >= 1 AND end_at > start_at AND service_id > 0")
@Builder 
@Getter 
@ToString 
@AllArgsConstructor 
@NoArgsConstructor 
public class MatchEntity {
    @Id 
    @GeneratedValue (strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "title", length = 50, nullable = false)
    private String title;
    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "start_at", columnDefinition = "DATETIME", nullable = false)
    private LocalDateTime startAt;
    @Column(name = "end_at", columnDefinition = "DATETIME", nullable = false)
    private LocalDateTime endAt;

    @Column(name = "max_participant", nullable = false)
    private Integer maxParticipant;

    @Builder.Default
    @Enumerated (EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "status", length = 10, nullable = false)
    @ColumnDefault ("'RECRUITING'")
    private MatchStatus status = MatchStatus.RECRUITING;

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

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "skill_level", length = 30, nullable = false)
    @ColumnDefault("'BEGINNER'")
    private SkillLevel skillLevel = SkillLevel.BEGINNER;

    @Enumerated (EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "sport_type", length = 15, nullable = false)
    private SportType sportType;

    @Enumerated (EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "gender_group", length = 10, nullable = false)
    private GenderGroup genderGroup;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "service_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_match_service"))
    @ToString.Exclude
    private ServiceEntity service;

    // Match의 Status를 CLOSED 로 변경하는 메서드
    public void closeRecruitment() {
        this.status = MatchStatus.CLOSED;
    }

    // Match의 Status를 RECRUITING 로 변경하는 메서드
    public void reopenRecruitment() {
        this.status = MatchStatus.RECRUITING;
    }

    public void update(
            String title,
            String description,
            LocalDateTime startAt,
            LocalDateTime endAt,
            Integer maxParticipant,
            SkillLevel skillLevel,
            GenderGroup genderGroup
    ) {
        this.title = title;
        this.description = description;
        this.startAt = startAt;
        this.endAt = endAt;
        this.maxParticipant = maxParticipant;
        this.skillLevel = skillLevel;
        this.genderGroup = genderGroup;
    }
}
