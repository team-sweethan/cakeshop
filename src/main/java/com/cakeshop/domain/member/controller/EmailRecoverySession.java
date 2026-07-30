package com.cakeshop.domain.member.controller;

import java.time.Instant;
import java.util.List;

record EmailRecoverySession(
        List<String> emails,
        String token,
        Instant expiresAt) {

    EmailRecoverySession {
        emails = List.copyOf(emails);
    }

    boolean canSelect(String requestedToken, int selectedIndex, Instant now) {
        return requestedToken != null
                && token.equals(requestedToken)
                && now.isBefore(expiresAt)
                && selectedIndex >= 0
                && selectedIndex < emails.size();
    }
}
