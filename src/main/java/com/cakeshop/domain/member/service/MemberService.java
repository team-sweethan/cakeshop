package com.cakeshop.domain.member.service;

import com.cakeshop.domain.coupon.service.CouponMemberCommandService;
import com.cakeshop.domain.member.dto.form.ProfileUpdateForm;
import com.cakeshop.domain.member.dto.form.SignupForm;
import com.cakeshop.domain.member.dto.view.EmailRecoveryResult;
import com.cakeshop.domain.member.dto.view.MemberProfileView;
import com.cakeshop.domain.member.dto.view.PasswordRecoveryTarget;
import com.cakeshop.domain.member.dto.view.PasswordResetResult;
import com.cakeshop.domain.member.dto.view.RecoveredEmailView;
import com.cakeshop.domain.member.dto.view.SignupEmailVerification;
import com.cakeshop.domain.member.entity.Member;
import com.cakeshop.domain.member.entity.MemberStatus;
import com.cakeshop.domain.member.error.MemberErrorCode;
import com.cakeshop.domain.member.mapper.MemberMapper;
import com.cakeshop.global.error.BusinessException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MemberService {

    private final MemberMapper memberMapper;
    private final PasswordEncoder passwordEncoder;
    private final CouponMemberCommandService couponMemberCommandService;
    private final EmailVerificationService emailVerificationService;

    /**
     * 회원가입 로직
     */
    @Transactional
    public boolean join(SignupForm form, SignupEmailVerification verification) {
        String email = form.getEmail().trim().toLowerCase(java.util.Locale.ROOT);
        if (memberMapper.findByEmail(email).isPresent()) {
            throw new BusinessException(MemberErrorCode.DUPLICATE_EMAIL);
        }
        if (verification == null || !email.equals(verification.email())) {
            return false;
        }
        if (!emailVerificationService.consumeSignupVerification(
                verification.verificationId(), email)) {
            return false;
        }

        String encodedPassword = passwordEncoder.encode(form.getPassword());

        Member member = Member.builder()
                .email(email)
                .password(encodedPassword)
                .name(form.getName())
                .nickname(form.getNickname())
                .phone(form.getPhone())
                .birthDate(form.getBirthDate())
                .role("USER")
                .build();
        memberMapper.join(member);
        if (member.getId() == null) {
            throw new BusinessException(MemberErrorCode.UPDATE_FAILED);
        }
        // 회원 INSERT와 신규 회원 대상 쿠폰 발급은 같은 트랜잭션에서 함께 확정한다.
        couponMemberCommandService.issueNewMemberCoupons(member.getId());
        return true;
    }

    /**
     * 이메일 중복 확인 (AJAX 요청용)
     */
    @Transactional(readOnly = true)
    public boolean checkEmailDuplicate(String email) {
        return memberMapper.findByEmail(email).isPresent();
    }

    /** 로그인 뒤 상태가 바뀐 세션도 차단할 수 있도록 현재 회원 상태를 DB에서 확인한다. */
    @Transactional(readOnly = true)
    public boolean isActiveMember(long memberId) {
        return memberId > 0 && memberMapper.existsActiveMember(memberId);
    }

    @Transactional(readOnly = true)
    public EmailRecoveryResult findEmails(String name, LocalDate birthDate, String phone) {
        String normalizedName = name.trim();
        String normalizedPhone = phone.replace("-", "");
        List<String> emails =
                memberMapper.findEmailsByMemberInfo(normalizedName, birthDate, normalizedPhone);
        List<RecoveredEmailView> views = IntStream.range(0, emails.size())
                .mapToObj(index -> new RecoveredEmailView(index, maskEmail(emails.get(index))))
                .toList();
        return new EmailRecoveryResult(emails, views);
    }

    @Transactional(readOnly = true)
    public Optional<PasswordRecoveryTarget> findPasswordRecoveryMember(
            String email,
            String name,
            LocalDate birthDate,
            String phone) {
        return memberMapper.findPasswordRecoveryMember(
                        email.trim(),
                        name.trim(),
                        birthDate,
                        phone.replace("-", ""))
                .map(member -> new PasswordRecoveryTarget(member.getId(), member.getEmail()));
    }

    @Transactional
    public PasswordResetResult resetPassword(Long memberId, String newPassword) {
        Optional<String> currentPassword =
                memberMapper.findActivePasswordForUpdate(memberId);
        if (currentPassword.isEmpty()) {
            return PasswordResetResult.UNAVAILABLE;
        }
        if (passwordEncoder.matches(newPassword, currentPassword.get())) {
            return PasswordResetResult.SAME_AS_CURRENT;
        }

        String encodedPassword = passwordEncoder.encode(newPassword);
        if (memberMapper.updatePasswordForActiveMember(memberId, encodedPassword) != 1) {
            return PasswordResetResult.UNAVAILABLE;
        }
        return PasswordResetResult.SUCCESS;
    }

    @Transactional
    public void updateMemberInfo(String email, ProfileUpdateForm form) {
        Member member = memberMapper.findByEmail(email)
                .orElseThrow(() -> new BusinessException(MemberErrorCode.NOT_FOUND));

        if (form.isPasswordChangeRequested()) {
            if (!passwordEncoder.matches(form.getCurrentPassword(), member.getPassword())) {
                throw new BusinessException(MemberErrorCode.INVALID_CURRENT_PASSWORD);
            }
            if (!form.getNewPassword().equals(form.getNewPasswordConfirm())) {
                throw new BusinessException(MemberErrorCode.PASSWORD_MISMATCH);
            }
            member.setPassword(passwordEncoder.encode(form.getNewPassword()));
        } else {
            // MyBatis 동적 UPDATE가 비밀번호 컬럼을 제외하도록 조회한 해시를 비운다.
            member.setPassword(null);
        }

        member.setName(form.getName());
        member.setNickname(form.getNickname());
        member.setPhone(form.getPhone());

        if (memberMapper.update(member) == 0) {
            throw new BusinessException(MemberErrorCode.UPDATE_FAILED);
        }
    }

    @Transactional(readOnly = true)
    public MemberProfileView getMemberProfile(String email) {
        Member member = memberMapper.findByEmail(email)
                .orElseThrow(() -> new BusinessException(MemberErrorCode.NOT_FOUND));
        return new MemberProfileView(
                member.getEmail(),
                member.getName(),
                member.getNickname(),
                member.getPhone(),
                member.getBirthDate());
    }

    @Transactional
    public void withdraw(String email, String currentPassword) {
        Member member = memberMapper.findByEmail(email)
                .orElseThrow(() -> new BusinessException(MemberErrorCode.NOT_FOUND));
        if (member.getPassword() == null
                || !passwordEncoder.matches(currentPassword, member.getPassword())) {
            throw new BusinessException(MemberErrorCode.INVALID_CURRENT_PASSWORD);
        }
        if (member.getStatus() == null
                || !member.getStatus().canTransitionTo(MemberStatus.WITHDRAWN)) {
            throw new BusinessException(MemberErrorCode.INVALID_STATUS_TRANSITION);
        }
        if (memberMapper.withdrawById(member.getId(), MemberStatus.WITHDRAWN) == 0) {
            throw new BusinessException(MemberErrorCode.WITHDRAW_FAILED);
        }
    }

    private String maskEmail(String email) {
        int atIndex = email.indexOf('@');
        if (atIndex <= 0) {
            return "***";
        }

        String localPart = email.substring(0, atIndex);
        String domainPart = email.substring(atIndex);
        int visibleLength = localPart.length() >= 3 ? 2 : Math.max(localPart.length() - 1, 0);
        return localPart.substring(0, visibleLength)
                + "*".repeat(localPart.length() - visibleLength)
                + domainPart;
    }

}
