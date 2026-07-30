package com.cakeshop.domain.member.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class EmailRecoverySessionTests {

    private static final Instant NOW = Instant.parse("2026-07-30T00:00:00Z");

    @Test
    void canSelect_validTokenIndexAndTime_returnsTrue() {
        EmailRecoverySession session = session();

        assertThat(session.canSelect("recovery-token", 0, NOW)).isTrue();
    }

    @Test
    void canSelect_invalidTokenOrIndex_returnsFalse() {
        EmailRecoverySession session = session();

        assertThat(session.canSelect("other-token", 0, NOW)).isFalse();
        assertThat(session.canSelect("recovery-token", 1, NOW)).isFalse();
    }

    @Test
    void canSelect_expiredSession_returnsFalse() {
        EmailRecoverySession session = session();

        assertThat(session.canSelect(
                "recovery-token",
                0,
                NOW.plusSeconds(301))).isFalse();
    }

    private EmailRecoverySession session() {
        return new EmailRecoverySession(
                List.of("member@example.com"),
                "recovery-token",
                NOW.plusSeconds(300));
    }
}
