package com.cakeshop.domain.member.entity;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** 이메일 인증번호의 만료·검증·일회성 사용 상태를 저장한다. */
@Getter
@Setter
public class EmailVerification {

    private Long id;
    private String email;
    private EmailVerificationPurpose purpose;
    private String codeHash;
    private LocalDateTime expiresAt;
    private int attemptCount;
    private LocalDateTime verifiedAt;
    private LocalDateTime consumedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
