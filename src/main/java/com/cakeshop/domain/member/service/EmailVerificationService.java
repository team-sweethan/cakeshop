package com.cakeshop.domain.member.service;

import com.cakeshop.domain.member.dto.form.SignupForm;
import com.cakeshop.domain.member.dto.view.PasswordResetEmailVerification;
import com.cakeshop.domain.member.dto.view.SignupEmailVerification;
import com.cakeshop.domain.member.entity.EmailVerification;
import com.cakeshop.domain.member.entity.EmailVerificationPurpose;
import com.cakeshop.domain.member.entity.Member;
import com.cakeshop.domain.member.entity.MemberStatus;
import com.cakeshop.domain.member.error.MemberErrorCode;
import com.cakeshop.domain.member.error.EmailVerificationSendException;
import com.cakeshop.domain.member.mapper.EmailVerificationMapper;
import com.cakeshop.domain.member.mapper.MemberMapper;
import com.cakeshop.global.error.BusinessException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
@RequiredArgsConstructor
public class EmailVerificationService {

    private static final Duration CODE_TTL = Duration.ofMinutes(5);
    private static final Duration VERIFIED_TTL = Duration.ofMinutes(10);
    private static final Duration RESEND_INTERVAL = Duration.ofSeconds(60);
    private static final Duration REQUEST_WINDOW = Duration.ofHours(1);
    private static final Duration RETENTION = Duration.ofDays(7);
    private static final int MAX_REQUESTS_PER_WINDOW = 5;
    private static final int REQUEST_LOCK_TIMEOUT_SECONDS = 3;
    private static final String DUMMY_CODE_HASH =
            "$2a$10$wRIE78x8sm..uLtbp9LHde7l6wUWQD3NjPvThQaXvZ3PpXfW6wwX.";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final EmailVerificationMapper emailVerificationMapper;
    private final EmailVerificationAttemptService emailVerificationAttemptService;
    private final MemberMapper memberMapper;
    private final EmailSender emailSender;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    /** 회원가입 이메일로 새 인증번호를 발송한다. */
    @Transactional(noRollbackFor = EmailVerificationSendException.class)
    public void sendSignupCode(String rawEmail) {
        String email = normalizeAndValidateEmail(rawEmail);
        sendCodeWithLock(email, EmailVerificationPurpose.SIGNUP);
    }

    /** 비밀번호 재설정 이메일로 새 인증번호를 발송한다. */
    @Transactional
    public void sendPasswordResetCode(String rawEmail) {
        String email = normalizeAndValidateEmail(rawEmail);
        sendCodeWithLock(email, EmailVerificationPurpose.PASSWORD_RESET);
    }

    private void sendCodeWithLock(
            String email,
            EmailVerificationPurpose purpose) {
        if (emailVerificationMapper.acquireRequestLock(
                email, purpose, REQUEST_LOCK_TIMEOUT_SECONDS) != 1) {
            throw new BusinessException(MemberErrorCode.EMAIL_VERIFICATION_RATE_LIMITED);
        }
        boolean releaseImmediately = !TransactionSynchronizationManager.isSynchronizationActive();
        if (!releaseImmediately) {
            releaseRequestLockAfterTransaction(email, purpose);
        }
        try {
            if (purpose == EmailVerificationPurpose.SIGNUP) {
                if (memberMapper.findByEmail(email).isPresent()) {
                    throw new BusinessException(MemberErrorCode.DUPLICATE_EMAIL);
                }
            } else if (findPasswordResetMember(email).isEmpty()) {
                return;
            }
            sendCodeWithinLock(email, purpose);
        } finally {
            if (releaseImmediately) {
                emailVerificationMapper.releaseRequestLock(email, purpose);
            }
        }
    }

    private void sendCodeWithinLock(String email, EmailVerificationPurpose purpose) {
        LocalDateTime now = LocalDateTime.now(clock);
        emailVerificationMapper.findLatest(email, purpose)
                .filter(latest -> latest.getCreatedAt()
                        .plus(RESEND_INTERVAL)
                        .isAfter(now))
                .ifPresent(latest -> {
                    throw new BusinessException(
                            MemberErrorCode.EMAIL_VERIFICATION_RESEND_TOO_SOON);
                });
        if (emailVerificationMapper.countRequestsSince(
                email,
                purpose,
                now.minus(REQUEST_WINDOW)) >= MAX_REQUESTS_PER_WINDOW) {
            throw new BusinessException(MemberErrorCode.EMAIL_VERIFICATION_RATE_LIMITED);
        }

        String code = "%06d".formatted(SECURE_RANDOM.nextInt(1_000_000));
        EmailVerification verification = new EmailVerification();
        verification.setEmail(email);
        verification.setPurpose(purpose);
        verification.setCodeHash(passwordEncoder.encode(code));
        verification.setExpiresAt(now.plus(CODE_TTL));
        if (emailVerificationMapper.insert(verification) != 1) {
            throw new BusinessException(MemberErrorCode.EMAIL_VERIFICATION_SAVE_FAILED);
        }

        emailSender.sendVerificationCode(email, code, CODE_TTL);
    }

    /** 가장 최근 회원가입 인증번호를 확인한다. */
    @Transactional
    public SignupEmailVerification verifySignupCode(String rawEmail, String code) {
        String email = normalizeAndValidateEmail(rawEmail);
        EmailVerification verification = verifyCode(
                email, code, EmailVerificationPurpose.SIGNUP);
        return new SignupEmailVerification(verification.getId(), email);
    }

    /** 비밀번호 재설정 이메일의 가장 최근 인증번호를 확인한다. */
    @Transactional
    public PasswordResetEmailVerification verifyPasswordResetCode(
            String rawEmail,
            String code) {
        String email = normalizeAndValidateEmail(rawEmail);
        EmailVerification verification = verifyCode(
                email, code, EmailVerificationPurpose.PASSWORD_RESET);
        Member member = findPasswordResetMember(email)
                .orElseThrow(() -> new BusinessException(
                        MemberErrorCode.EMAIL_VERIFICATION_INVALID));
        return new PasswordResetEmailVerification(
                verification.getId(),
                member.getId(),
                member.getEmail(),
                verificationExpiresAt(verification));
    }

    private EmailVerification verifyCode(
            String email,
            String code,
            EmailVerificationPurpose purpose) {
        LocalDateTime now = LocalDateTime.now(clock);
        EmailVerification verification = emailVerificationMapper.findLatest(email, purpose)
                .orElse(null);
        if (verification == null) {
            rejectInvalidCode(code, purpose);
        }

        if (verification.getVerifiedAt() != null) {
            boolean canVerify = verification.getAttemptCount() < 5
                    && verification.getConsumedAt() == null
                    && verification.getExpiresAt().isAfter(now)
                    && verification.getVerifiedAt().plus(VERIFIED_TTL).isAfter(now)
                    && code != null
                    && code.matches("\\d{6}");
            if (!canVerify) {
                rejectInvalidCode(code, purpose);
            }
            if (passwordEncoder.matches(code, verification.getCodeHash())) {
                return verification;
            }
            emailVerificationAttemptService.recordFailure(verification.getId(), now);
            throw new BusinessException(MemberErrorCode.EMAIL_VERIFICATION_INVALID);
        }
        if (verification.getConsumedAt() != null
                || !verification.getExpiresAt().isAfter(now)
                || verification.getAttemptCount() >= 5
                || code == null
                || !code.matches("\\d{6}")) {
            rejectInvalidCode(code, purpose);
        }
        if (!passwordEncoder.matches(code, verification.getCodeHash())) {
            emailVerificationAttemptService.recordFailure(verification.getId(), now);
            throw new BusinessException(MemberErrorCode.EMAIL_VERIFICATION_INVALID);
        }
        if (emailVerificationMapper.markVerified(
                verification.getId(),
                email,
                purpose,
                now) != 1) {
            throw new BusinessException(MemberErrorCode.EMAIL_VERIFICATION_INVALID);
        }
        verification.setVerifiedAt(now);
        return verification;
    }

    private void rejectInvalidCode(String code, EmailVerificationPurpose purpose) {
        if (purpose == EmailVerificationPurpose.PASSWORD_RESET) {
            passwordEncoder.matches(code == null ? "" : code, DUMMY_CODE_HASH);
        }
        throw new BusinessException(MemberErrorCode.EMAIL_VERIFICATION_INVALID);
    }

    private Instant verificationExpiresAt(EmailVerification verification) {
        return verification.getVerifiedAt()
                .plus(VERIFIED_TTL)
                .atZone(clock.getZone())
                .toInstant();
    }

    /** 인증된 이메일을 회원가입에서 한 번만 사용 처리한다. */
    @Transactional
    public boolean consumeSignupVerification(Long verificationId, String rawEmail) {
        return consumeVerification(
                verificationId, rawEmail, EmailVerificationPurpose.SIGNUP);
    }

    /** 인증된 이메일을 비밀번호 재설정에서 한 번만 사용 처리한다. */
    @Transactional
    public boolean consumePasswordResetVerification(Long verificationId, String rawEmail) {
        return consumeVerification(
                verificationId, rawEmail, EmailVerificationPurpose.PASSWORD_RESET);
    }

    private boolean consumeVerification(
            Long verificationId,
            String rawEmail,
            EmailVerificationPurpose purpose) {
        if (verificationId == null) {
            return false;
        }
        String email = normalizeAndValidateEmail(rawEmail);
        LocalDateTime now = LocalDateTime.now(clock);
        return findConsumableVerification(
                        verificationId,
                        email,
                        purpose,
                        now.minus(VERIFIED_TTL))
                .map(verification ->
                        emailVerificationMapper.markConsumed(verification.getId(), now) == 1)
                .orElse(false);
    }

    private Optional<EmailVerification> findConsumableVerification(
            Long verificationId,
            String email,
            EmailVerificationPurpose purpose,
            LocalDateTime verifiedSince) {
        return emailVerificationMapper.findLatestVerifiedByIdForUpdate(
                verificationId, email, purpose, verifiedSince);
    }

    private Optional<Member> findPasswordResetMember(String email) {
        return memberMapper.findByEmail(email)
                .filter(member -> MemberStatus.ACTIVE == member.getStatus())
                .filter(member -> "USER".equals(member.getRole()))
                .filter(member -> member.getPassword() != null);
    }

    /** 보관 기간이 지난 이메일 인증 요청을 삭제한다. */
    @Transactional
    public int deleteExpiredVerifications() {
        return emailVerificationMapper.deleteExpiredBefore(
                LocalDateTime.now(clock).minus(RETENTION));
    }

    private String normalizeAndValidateEmail(String rawEmail) {
        String email = SignupForm.normalizeEmail(rawEmail);
        if (!SignupForm.isEmailFormatValid(email)) {
            throw new BusinessException(MemberErrorCode.INVALID_EMAIL);
        }
        return email;
    }

    private void releaseRequestLockAfterTransaction(
            String email,
            EmailVerificationPurpose purpose) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                emailVerificationMapper.releaseRequestLock(email, purpose);
            }
        });
    }
}
