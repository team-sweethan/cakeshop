package com.cakeshop.domain.member.service;

import com.cakeshop.domain.member.mapper.EmailVerificationMapper;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class EmailVerificationAttemptService {

    private final EmailVerificationMapper emailVerificationMapper;

    /** 인증 실패 횟수를 현재 인증 트랜잭션과 분리해 확정한다. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(Long verificationId, LocalDateTime attemptedAt) {
        emailVerificationMapper.incrementAttemptCount(verificationId, attemptedAt);
    }
}
