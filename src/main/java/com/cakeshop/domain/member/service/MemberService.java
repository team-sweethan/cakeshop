package com.cakeshop.domain.member.service;

import com.cakeshop.domain.member.dto.form.ProfileUpdateForm;
import com.cakeshop.domain.member.dto.form.SignupForm;
import com.cakeshop.domain.member.entity.Member;
import com.cakeshop.domain.member.mapper.MemberMapper;
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
            throw new IllegalArgumentException("이미 사용 중인 이메일입니다.");
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

    // 회원정보 수정
    public void updateMemberInfo(String email, ProfileUpdateForm form) {
        // 1. DB에서 조회 (Optional 사용)
        Member member = memberMapper.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 회원입니다."));

        // 2. 비밀번호 검증 (현재 비밀번호)
        if (!passwordEncoder.matches(form.getCurrentPassword(), member.getPassword())) {
            throw new IllegalArgumentException("현재 비밀번호가 일치하지 않습니다.");
        }

        // 3. 새 비밀번호 확인 일치 여부
        if (!form.getNewPassword().equals(form.getNewPasswordConfirm())) {
            throw new IllegalArgumentException("새 비밀번호가 일치하지 않습니다.");
        }

        // 4. 값 업데이트
        member.setName(form.getName());
        member.setPhone(form.getPhone());
        member.setPassword(passwordEncoder.encode(form.getNewPassword())); // 암호화 저장

        // 5. DB 업데이트 및 결과 확인
        if (memberMapper.update(member) == 0) {
            throw new RuntimeException("회원 정보 수정에 실패했습니다.");
        }
    }
    public Member getMemberByEmail(String email) {
        return memberMapper.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("회원을 찾을 수 없습니다."));
    }
}
