package com.cakeshop.domain.member.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.cakeshop.domain.member.entity.EmailVerification;
import com.cakeshop.domain.member.entity.EmailVerificationPurpose;
import com.cakeshop.global.config.MariaDbIntegrationTest;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.jdbc.core.JdbcTemplate;

@MybatisTest
@MariaDbIntegrationTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class EmailVerificationMapperTests {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 11, 18, 0);

    @Autowired
    private EmailVerificationMapper emailVerificationMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void requestLock_sameEmailAndPurpose_canAcquireAndRelease() {
        assertThat(emailVerificationMapper.acquireRequestLock(
                "lock@example.com", EmailVerificationPurpose.SIGNUP, 1)).isOne();
        assertThat(emailVerificationMapper.releaseRequestLock(
                "lock@example.com", EmailVerificationPurpose.SIGNUP)).isOne();
    }

    @Test
    void findLatest_multipleRequests_returnsNewestRequest() {
        EmailVerification first = insert("latest@example.com", NOW.plusMinutes(5));
        EmailVerification second = insert("latest@example.com", NOW.plusMinutes(6));

        EmailVerification latest = emailVerificationMapper.findLatest(
                        "latest@example.com",
                        EmailVerificationPurpose.SIGNUP)
                .orElseThrow();

        assertThat(latest.getId()).isEqualTo(second.getId());
        assertThat(latest.getId()).isNotEqualTo(first.getId());
    }

    @Test
    void verificationLifecycle_validRequest_canVerifyAndConsumeOnce() {
        EmailVerification verification = insert("lifecycle@example.com", NOW.plusMinutes(5));

        assertThat(emailVerificationMapper.markVerified(
                verification.getId(),
                verification.getEmail(),
                EmailVerificationPurpose.SIGNUP,
                NOW)).isOne();
        EmailVerification verified = emailVerificationMapper.findVerifiedByIdForUpdate(
                        verification.getId(),
                        verification.getEmail(),
                        EmailVerificationPurpose.SIGNUP,
                        NOW.minusMinutes(10))
                .orElseThrow();
        assertThat(emailVerificationMapper.markConsumed(verified.getId(), NOW.plusMinutes(1)))
                .isOne();
        assertThat(emailVerificationMapper.markConsumed(verified.getId(), NOW.plusMinutes(2)))
                .isZero();
    }

    @Test
    void incrementAttemptCount_fifthFailure_blocksFurtherChanges() {
        EmailVerification verification = insert("attempt@example.com", NOW.plusMinutes(5));

        for (int attempt = 0; attempt < 5; attempt++) {
            assertThat(emailVerificationMapper.incrementAttemptCount(verification.getId(), NOW))
                    .isOne();
        }

        assertThat(emailVerificationMapper.incrementAttemptCount(verification.getId(), NOW))
                .isZero();
        assertThat(emailVerificationMapper.markVerified(
                verification.getId(),
                verification.getEmail(),
                EmailVerificationPurpose.SIGNUP,
                NOW)).isZero();
    }

    @Test
    void resend_newRequest_doesNotInvalidateVerifiedSessionRequest() {
        EmailVerification oldRequest = insert("resend@example.com", NOW.plusMinutes(5));
        assertThat(emailVerificationMapper.markVerified(
                oldRequest.getId(),
                oldRequest.getEmail(),
                EmailVerificationPurpose.SIGNUP,
                NOW)).isOne();

        insert("resend@example.com", NOW.plusMinutes(6));

        assertThat(emailVerificationMapper.findVerifiedByIdForUpdate(
                oldRequest.getId(),
                oldRequest.getEmail(),
                EmailVerificationPurpose.SIGNUP,
                NOW.minusMinutes(10))).isPresent();
        assertThat(emailVerificationMapper.markVerified(
                oldRequest.getId(),
                oldRequest.getEmail(),
                EmailVerificationPurpose.SIGNUP,
                NOW.plusMinutes(1))).isZero();
    }

    @Test
    void countAndCleanup_requests_usesPurposeAndRetentionCutoff() {
        EmailVerification oldRequest = insert("cleanup@example.com", NOW.minusDays(8));
        jdbcTemplate.update(
                "UPDATE email_verifications SET created_at = ? WHERE id = ?",
                NOW.minusHours(2),
                oldRequest.getId());
        insert("cleanup@example.com", NOW.plusMinutes(5));

        assertThat(emailVerificationMapper.countRequestsSince(
                "cleanup@example.com",
                EmailVerificationPurpose.SIGNUP,
                NOW.minusHours(1))).isOne();
        assertThat(emailVerificationMapper.deleteExpiredBefore(NOW.minusDays(7))).isOne();
        assertThat(emailVerificationMapper.findLatest(
                "cleanup@example.com",
                EmailVerificationPurpose.SIGNUP)).isPresent();
    }

    private EmailVerification insert(String email, LocalDateTime expiresAt) {
        EmailVerification verification = new EmailVerification();
        verification.setEmail(email);
        verification.setPurpose(EmailVerificationPurpose.SIGNUP);
        verification.setCodeHash("encoded-code");
        verification.setExpiresAt(expiresAt);

        assertThat(emailVerificationMapper.insert(verification)).isOne();
        assertThat(verification.getId()).isNotNull();
        return verification;
    }
}
