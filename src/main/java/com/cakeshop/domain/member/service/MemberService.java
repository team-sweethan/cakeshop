package com.cakeshop.domain.member.service;

import com.cakeshop.domain.member.dto.form.ProfileUpdateForm;
import com.cakeshop.domain.member.dto.form.SignupForm;
import com.cakeshop.domain.member.dto.view.EmailRecoveryResult;
import com.cakeshop.domain.member.dto.view.MemberProfileView;
import com.cakeshop.domain.member.dto.view.RecoveredEmailView;
import com.cakeshop.domain.member.entity.Member;
import com.cakeshop.domain.member.entity.MemberStatus;
import com.cakeshop.domain.member.error.MemberErrorCode;
import com.cakeshop.domain.member.mapper.MemberMapper;
import com.cakeshop.global.error.BusinessException;
import java.time.LocalDate;
import java.util.List;
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

    /**
     * 회원가입 로직
     */
    @Transactional
    public void join(SignupForm form) {
        if (memberMapper.findByEmail(form.getEmail()).isPresent()) {
            throw new BusinessException(MemberErrorCode.DUPLICATE_EMAIL);
        }

        String encodedPassword = passwordEncoder.encode(form.getPassword());

        Member member = Member.builder()
                .email(form.getEmail())
                .password(encodedPassword)
                .name(form.getName())
                .nickname(form.getNickname())
                .phone(form.getPhone())
                .birthDate(form.getBirthDate())
                .role("USER")
                .build();
        memberMapper.join(member);
    }

    /**
     * 이메일 중복 확인 (AJAX 요청용)
     */
    @Transactional(readOnly = true)
    public boolean checkEmailDuplicate(String email) {
        return memberMapper.findByEmail(email).isPresent();
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
    public void withdraw(String email) {
        Member member = memberMapper.findByEmail(email)
                .orElseThrow(() -> new BusinessException(MemberErrorCode.NOT_FOUND));
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
