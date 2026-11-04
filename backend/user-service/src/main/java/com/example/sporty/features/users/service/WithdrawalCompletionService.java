package com.example.sporty.features.users.service;

import com.example.sporty.features.users.domain.dto.WithdrawalCompletedEvent;
import com.example.sporty.features.users.domain.entity.UserStatus;
import com.example.sporty.features.users.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class WithdrawalCompletionService {

    private final UserRepository userRepository;

    @Transactional
    public boolean complete(WithdrawalCompletedEvent event) {
        validate(event);

        int updated = userRepository.completeWithdrawal(
                event.userId(),
                event.sagaId(),
                UserStatus.WITHDRAWAL_PENDING,
                UserStatus.WITHDRAWN
        );

        if (updated == 1) {
            log.info(
                    "[KAFKA] 탈퇴 확정 반영 eventId={} userId={} sagaId={}",
                    event.eventId(),
                    event.userId(),
                    event.sagaId()
            );
            return true;
        }

        log.info(
                "[KAFKA] 탈퇴 확정 상태 변경 무시 "
                        + "eventId={} userId={} sagaId={}",
                event.eventId(),
                event.userId(),
                event.sagaId()
        );
        return false;
    }

    @Transactional(readOnly = true)
    public boolean isWithdrawn(Long userId) {
        return userRepository.findById(userId)
                .map(user -> user.getStatus() == UserStatus.WITHDRAWN)
                .orElse(false);
    }

    private void validate(WithdrawalCompletedEvent event) {
        if (event == null
                || event.eventId() == null
                || event.eventId().isBlank()
                || event.sagaId() == null
                || event.sagaId().isBlank()
                || event.userId() == null
                || event.userId() <= 0) {
            throw new IllegalArgumentException(
                    "탈퇴 완료 이벤트의 필수 식별값이 올바르지 않습니다."
            );
        }
    }
}