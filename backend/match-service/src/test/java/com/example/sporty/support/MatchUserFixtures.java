package com.example.sporty.support;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import jakarta.persistence.EntityManager;
import com.example.sporty.features.users.domain.entity.Gender;
import com.example.sporty.features.users.domain.entity.UserEntity;

/** Persist users and map test labels to generated database IDs. */
public final class MatchUserFixtures {
    private MatchUserFixtures() {}

    public static Map<Long, UserEntity> create(EntityManager em, int count) {
        Map<Long, UserEntity> users = new HashMap<>();
        for (long label = 1; label <= count; label++) {
            UserEntity user = UserEntity.builder()
                    .email(UUID.randomUUID() + "@test.local")
                    .password("test-only-password")
                    .gender(Gender.MALE)
                    .build();
            em.persist(user);
            users.put(label, user);
        }
        return users;
    }
}
