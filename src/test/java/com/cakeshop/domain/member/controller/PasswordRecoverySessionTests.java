package com.cakeshop.domain.member.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class PasswordRecoverySessionTests {

    @Test
    void isValid_beforeExpiry_returnsTrue() {
        Instant now = Instant.parse("2026-07-30T00:00:00Z");
        PasswordRecoverySession session =
                new PasswordRecoverySession(
                        11L, 1L, "member@example.com", now.plusSeconds(300));

        assertThat(session.isValid(now)).isTrue();
    }

    @Test
    void isValid_atOrAfterExpiry_returnsFalse() {
        Instant expiry = Instant.parse("2026-07-30T00:05:00Z");
        PasswordRecoverySession session =
                new PasswordRecoverySession(11L, 1L, "member@example.com", expiry);

        assertThat(session.isValid(expiry)).isFalse();
        assertThat(session.isValid(expiry.plusSeconds(1))).isFalse();
    }
}
