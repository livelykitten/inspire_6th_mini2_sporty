package com.example.sporty.support;

import java.util.HashMap;
import java.util.Map;

import jakarta.persistence.EntityManager;

/** Map test labels to member IDs without depending on user-service entities. */
public final class MatchUserFixtures {
    private MatchUserFixtures() {}

    public static Map<Long, Long> create(EntityManager em, int count) {
        Map<Long, Long> users = new HashMap<>();
        for (long label = 1; label <= count; label++) {
            users.put(label, label);
        }
        return users;
    }
}
