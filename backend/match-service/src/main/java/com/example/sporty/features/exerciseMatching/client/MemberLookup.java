package com.example.sporty.features.exerciseMatching.client;

import java.util.List;
import java.util.Map;

public interface MemberLookup {

    void validateActive(Long userId);

    Map<Long, MemberProfile> getProfiles(List<Long> userIds);
}
