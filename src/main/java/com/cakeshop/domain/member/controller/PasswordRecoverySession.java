package com.cakeshop.domain.member.controller;

import java.io.Serializable;
import java.time.Instant;

record PasswordRecoverySession(
        Long verificationId,
        Long memberId,
        String email,
        Instant expiresAt) implements Serializable {

    boolean isValid(Instant now) {
        return verificationId != null
                && memberId != null
                && email != null
                && expiresAt != null
                && now.isBefore(expiresAt);
    }
}
