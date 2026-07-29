package com.cakeshop.domain.member.service;

import com.cakeshop.domain.member.dto.form.ProfileUpdateForm;
import com.cakeshop.domain.member.dto.form.SignupForm;
import com.cakeshop.domain.member.dto.view.MemberProfileView;
import com.cakeshop.domain.member.entity.Member;
import com.cakeshop.domain.member.entity.MemberStatus;
import com.cakeshop.domain.member.error.MemberErrorCode;
import com.cakeshop.domain.member.mapper.MemberMapper;
import com.cakeshop.global.error.BusinessException;
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
                member.getPhone());
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

}
