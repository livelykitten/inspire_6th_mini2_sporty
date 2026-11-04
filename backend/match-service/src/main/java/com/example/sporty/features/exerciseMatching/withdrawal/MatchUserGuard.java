package com.example.sporty.features.exerciseMatching.withdrawal;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;

@Entity
@Table(name = "match_user_guard")
@Getter
@NoArgsConstructor
public class MatchUserGuard {
    @Id
    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "is_blocked", nullable = false)
    @ColumnDefault("0")
    private boolean blocked;

    public void block() { blocked = true; }
}
