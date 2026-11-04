package com.example.sporty.features.users.service;

import com.example.sporty.features.users.domain.dto.WithdrawalFailedEvent;
import com.example.sporty.features.users.domain.entity.UserStatus;
import com.example.sporty.features.users.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class WithdrawalCompensationService {

    private final UserRepository userRepository;

    @Transactional
    public boolean compensate(WithdrawalFailedEvent event) {
        validate(event);

        int updated = userRepository.compensateWithdrawal(
                event.userId(),
                event.sagaId(),
                UserStatus.WITHDRAWAL_PENDING,
                UserStatus.ACTIVE
        );

        if (updated == 1) {
            log.info("[KAFKA] match.withdrawal.failed userId={} sagaId={} eventId={} -> ACTIVE",
                    event.userId(), event.sagaId(), event.eventId());
            return true;
        }

        log.info("[KAFKA] 탈퇴 실패 보상 무시 userId={} sagaId={} eventId={}",
                event.userId(), event.sagaId(), event.eventId());
        return false;
    }

    private void validate(WithdrawalFailedEvent event) {
        if (event == null
                || event.eventId() == null
                || event.eventId().isBlank()
                || event.sagaId() == null
                || event.sagaId().isBlank()
                || event.userId() == null
                || event.userId() <= 0) {
            throw new IllegalArgumentException(
                    "탈퇴 실패 이벤트의 필수 식별값이 올바르지 않습니다."
            );
        }
    }
}
