package com.cakeshop.domain.member.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.inOrder;

import com.cakeshop.domain.member.entity.EmailVerification;
import com.cakeshop.domain.member.entity.EmailVerificationPurpose;
import com.cakeshop.domain.member.entity.Member;
import com.cakeshop.domain.member.entity.MemberStatus;
import com.cakeshop.domain.member.dto.view.PasswordResetEmailVerification;
import com.cakeshop.domain.member.dto.view.SignupEmailVerification;
import com.cakeshop.domain.member.error.MemberErrorCode;
import com.cakeshop.domain.member.mapper.EmailVerificationMapper;
import com.cakeshop.domain.member.mapper.MemberMapper;
import com.cakeshop.global.error.BusinessException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.InOrder;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class EmailVerificationServiceTests {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-08-11T09:00:00Z"),
            SEOUL);
    private static final LocalDateTime NOW = LocalDateTime.now(CLOCK);

    @Mock
    private EmailVerificationMapper emailVerificationMapper;

    @Mock
    private MemberMapper memberMapper;

    @Mock
    private EmailSender emailSender;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private EmailVerificationService emailVerificationService;

    @BeforeEach
    void setUp() {
        lenient().when(emailVerificationMapper.acquireRequestLock(
                any(), any(), anyInt())).thenReturn(1);
        emailVerificationService = new EmailVerificationService(
                emailVerificationMapper,
                memberMapper,
                emailSender,
                passwordEncoder,
                CLOCK);
    }

    @Test
    void sendSignupCode_availableEmail_savesHashAndSendsSixDigits() {
        when(memberMapper.findByEmail("user@example.com")).thenReturn(Optional.empty());
        when(emailVerificationMapper.findLatest(
                "user@example.com", EmailVerificationPurpose.SIGNUP))
                .thenReturn(Optional.empty());
        when(emailVerificationMapper.countRequestsSince(
                any(), any(), any())).thenReturn(0);
        when(emailVerificationMapper.insert(any())).thenReturn(1);

        emailVerificationService.sendSignupCode(" User@Example.com ");

        InOrder requestOrder = inOrder(emailVerificationMapper, memberMapper);
        requestOrder.verify(emailVerificationMapper).acquireRequestLock(
                "user@example.com", EmailVerificationPurpose.SIGNUP, 3);
        requestOrder.verify(memberMapper).findByEmail("user@example.com");

        ArgumentCaptor<EmailVerification> verificationCaptor =
                ArgumentCaptor.forClass(EmailVerification.class);
        ArgumentCaptor<String> codeCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailVerificationMapper).insert(verificationCaptor.capture());
        verify(emailSender).sendVerificationCode(
                org.mockito.ArgumentMatchers.eq("user@example.com"),
                codeCaptor.capture(),
                org.mockito.ArgumentMatchers.eq(Duration.ofMinutes(5)));

        EmailVerification saved = verificationCaptor.getValue();
        assertThat(codeCaptor.getValue()).matches("\\d{6}");
        assertThat(passwordEncoder.matches(codeCaptor.getValue(), saved.getCodeHash())).isTrue();
        assertThat(saved.getExpiresAt()).isEqualTo(NOW.plusMinutes(5));
    }

    @Test
    void sendSignupCode_recentRequest_rejectsResend() {
        when(memberMapper.findByEmail("user@example.com")).thenReturn(Optional.empty());
        EmailVerification latest = verification("user@example.com", "hash");
        latest.setCreatedAt(NOW.minusSeconds(30));
        when(emailVerificationMapper.findLatest(
                "user@example.com", EmailVerificationPurpose.SIGNUP))
                .thenReturn(Optional.of(latest));

        assertMemberError(
                () -> emailVerificationService.sendSignupCode("user@example.com"),
                MemberErrorCode.EMAIL_VERIFICATION_RESEND_TOO_SOON);
        verify(emailSender, never()).sendVerificationCode(any(), any(), any());
    }

    @Test
    void sendSignupCode_fiveRequestsWithinHour_rejectsRequest() {
        when(memberMapper.findByEmail("user@example.com")).thenReturn(Optional.empty());
        when(emailVerificationMapper.findLatest(
                "user@example.com", EmailVerificationPurpose.SIGNUP))
                .thenReturn(Optional.empty());
        when(emailVerificationMapper.countRequestsSince(
                any(), any(), any())).thenReturn(5);

        assertMemberError(
                () -> emailVerificationService.sendSignupCode("user@example.com"),
                MemberErrorCode.EMAIL_VERIFICATION_RATE_LIMITED);
        verify(emailSender, never()).sendVerificationCode(any(), any(), any());
    }

    @Test
    void sendPasswordResetCode_activePasswordMember_sendsWithResetPurpose() {
        when(memberMapper.findByEmail("user@example.com"))
                .thenReturn(Optional.of(passwordMember()));
        when(emailVerificationMapper.findLatest(
                "user@example.com", EmailVerificationPurpose.PASSWORD_RESET))
                .thenReturn(Optional.empty());
        when(emailVerificationMapper.countRequestsSince(any(), any(), any())).thenReturn(0);
        when(emailVerificationMapper.insert(any())).thenReturn(1);

        emailVerificationService.sendPasswordResetCode(" User@Example.com ");

        ArgumentCaptor<EmailVerification> verificationCaptor =
                ArgumentCaptor.forClass(EmailVerification.class);
        verify(emailVerificationMapper).insert(verificationCaptor.capture());
        assertThat(verificationCaptor.getValue().getPurpose())
                .isEqualTo(EmailVerificationPurpose.PASSWORD_RESET);
        verify(emailSender).sendVerificationCode(
                org.mockito.ArgumentMatchers.eq("user@example.com"),
                org.mockito.ArgumentMatchers.matches("\\d{6}"),
                org.mockito.ArgumentMatchers.eq(Duration.ofMinutes(5)));
    }

    @Test
    void sendPasswordResetCode_unknownEmail_returnsWithoutRevealingMember() {
        when(memberMapper.findByEmail("missing@example.com")).thenReturn(Optional.empty());

        emailVerificationService.sendPasswordResetCode("missing@example.com");

        verify(emailVerificationMapper, never()).insert(any());
        verify(emailSender, never()).sendVerificationCode(any(), any(), any());
    }

    @Test
    void verifySignupCode_matchingCode_marksLatestVerified() {
        EmailVerification latest = verification(
                "user@example.com",
                passwordEncoder.encode("123456"));
        when(emailVerificationMapper.findLatest(
                "user@example.com", EmailVerificationPurpose.SIGNUP))
                .thenReturn(Optional.of(latest));
        when(emailVerificationMapper.markVerified(
                latest.getId(),
                latest.getEmail(),
                EmailVerificationPurpose.SIGNUP,
                NOW)).thenReturn(1);

        SignupEmailVerification result =
                emailVerificationService.verifySignupCode("user@example.com", "123456");

        assertThat(result).isEqualTo(
                new SignupEmailVerification(latest.getId(), "user@example.com"));
        verify(emailVerificationMapper).markVerified(
                latest.getId(),
                latest.getEmail(),
                EmailVerificationPurpose.SIGNUP,
                NOW);
    }

    @Test
    void verifySignupCode_wrongCode_incrementsAttemptAndRejects() {
        EmailVerification latest = verification(
                "user@example.com",
                passwordEncoder.encode("123456"));
        when(emailVerificationMapper.findLatest(
                "user@example.com", EmailVerificationPurpose.SIGNUP))
                .thenReturn(Optional.of(latest));

        assertMemberError(
                () -> emailVerificationService.verifySignupCode(
                        "user@example.com", "654321"),
                MemberErrorCode.EMAIL_VERIFICATION_INVALID);
        verify(emailVerificationMapper).incrementAttemptCount(latest.getId(), NOW);
    }

    @Test
    void verifySignupCode_alreadyVerifiedStillRequiresMatchingCode() {
        EmailVerification latest = verification(
                "user@example.com",
                passwordEncoder.encode("123456"));
        latest.setVerifiedAt(NOW.minusMinutes(1));
        when(emailVerificationMapper.findLatest(
                "user@example.com", EmailVerificationPurpose.SIGNUP))
                .thenReturn(Optional.of(latest));

        assertMemberError(
                () -> emailVerificationService.verifySignupCode(
                        "user@example.com", "654321"),
                MemberErrorCode.EMAIL_VERIFICATION_INVALID);
        verify(emailVerificationMapper).incrementAttemptCount(latest.getId(), NOW);
    }

    @Test
    void verifyPasswordResetCode_matchingCode_bindsMemberAndVerification() {
        EmailVerification latest = verification(
                "user@example.com", passwordEncoder.encode("123456"));
        latest.setPurpose(EmailVerificationPurpose.PASSWORD_RESET);
        when(emailVerificationMapper.findLatest(
                "user@example.com", EmailVerificationPurpose.PASSWORD_RESET))
                .thenReturn(Optional.of(latest));
        when(emailVerificationMapper.markVerified(
                latest.getId(), latest.getEmail(), EmailVerificationPurpose.PASSWORD_RESET, NOW))
                .thenReturn(1);
        when(memberMapper.findByEmail("user@example.com"))
                .thenReturn(Optional.of(passwordMember()));

        PasswordResetEmailVerification result =
                emailVerificationService.verifyPasswordResetCode(
                        "user@example.com", "123456");

        assertThat(result).isEqualTo(
                new PasswordResetEmailVerification(1L, 7L, "user@example.com"));
    }

    @Test
    void consumeSignupVerification_verifiedRequest_consumesOnce() {
        EmailVerification verified = verification("user@example.com", "hash");
        verified.setVerifiedAt(NOW.minusMinutes(1));
        when(emailVerificationMapper.findVerifiedByIdForUpdate(
                verified.getId(),
                "user@example.com",
                EmailVerificationPurpose.SIGNUP,
                NOW.minusMinutes(10))).thenReturn(Optional.of(verified));
        when(emailVerificationMapper.markConsumed(verified.getId(), NOW)).thenReturn(1);

        assertThat(emailVerificationService.consumeSignupVerification(
                verified.getId(), "user@example.com"))
                .isTrue();

        verify(emailVerificationMapper).markConsumed(verified.getId(), NOW);
    }

    @Test
    void consumeSignupVerification_withoutVerification_returnsFalse() {
        when(emailVerificationMapper.findVerifiedByIdForUpdate(
                7L,
                "user@example.com",
                EmailVerificationPurpose.SIGNUP,
                NOW.minusMinutes(10))).thenReturn(Optional.empty());

        assertThat(emailVerificationService.consumeSignupVerification(7L, "user@example.com"))
                .isFalse();
    }

    @Test
    void consumePasswordResetVerification_usesPasswordResetPurpose() {
        EmailVerification verified = verification("user@example.com", "hash");
        verified.setPurpose(EmailVerificationPurpose.PASSWORD_RESET);
        verified.setVerifiedAt(NOW.minusMinutes(1));
        when(emailVerificationMapper.findVerifiedByIdForUpdate(
                verified.getId(),
                "user@example.com",
                EmailVerificationPurpose.PASSWORD_RESET,
                NOW.minusMinutes(10))).thenReturn(Optional.of(verified));
        when(emailVerificationMapper.markConsumed(verified.getId(), NOW)).thenReturn(1);

        assertThat(emailVerificationService.consumePasswordResetVerification(
                verified.getId(), "user@example.com")).isTrue();
    }

    @Test
    void deleteExpiredVerifications_usesSevenDayRetention() {
        when(emailVerificationMapper.deleteExpiredBefore(NOW.minusDays(7))).thenReturn(3);

        assertThat(emailVerificationService.deleteExpiredVerifications()).isEqualTo(3);

        verify(emailVerificationMapper).deleteExpiredBefore(NOW.minusDays(7));
    }

    private EmailVerification verification(String email, String codeHash) {
        EmailVerification verification = new EmailVerification();
        verification.setId(1L);
        verification.setEmail(email);
        verification.setPurpose(EmailVerificationPurpose.SIGNUP);
        verification.setCodeHash(codeHash);
        verification.setExpiresAt(NOW.plusMinutes(5));
        verification.setCreatedAt(NOW.minusMinutes(1));
        return verification;
    }

    private Member passwordMember() {
        return Member.builder()
                .id(7L)
                .email("user@example.com")
                .password("encoded-password")
                .role("USER")
                .status(MemberStatus.ACTIVE)
                .build();
    }

    private void assertMemberError(Runnable action, MemberErrorCode errorCode) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(errorCode));
    }
}
