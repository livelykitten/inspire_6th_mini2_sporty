package com.example.sporty.features.exerciseMatching.withdrawal;

import com.example.sporty.features.commons.exception.matches.WithdrawnUserFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
@RequiredArgsConstructor
public class MatchUserGuardService {
    private final MatchUserGuardRepository guards;

    @Transactional(propagation = Propagation.MANDATORY)
    public MatchUserGuard lock(Long userId) {
        guards.ensureExists(userId);
        return guards.lock(userId).orElseThrow();
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void requireUnblocked(Long userId) {
        if (lock(userId).isBlocked()) throw new WithdrawnUserFoundException();
    }
}
