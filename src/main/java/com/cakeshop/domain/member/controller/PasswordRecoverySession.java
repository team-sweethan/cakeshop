package com.cakeshop.domain.member.controller;

import java.time.Instant;

record PasswordRecoverySession(Long memberId, String email, Instant expiresAt) {

    boolean isValid(Instant now) {
        return memberId != null
                && email != null
                && expiresAt != null
                && now.isBefore(expiresAt);
    }
}
