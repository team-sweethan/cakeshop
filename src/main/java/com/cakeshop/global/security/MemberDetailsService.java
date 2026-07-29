package com.cakeshop.global.security;

import com.cakeshop.domain.member.dto.view.MemberAuthenticationView;
import com.cakeshop.domain.member.service.MemberAuthenticationService;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class MemberDetailsService implements UserDetailsService {

    private final MemberAuthenticationService memberAuthenticationService;

    public MemberDetailsService(MemberAuthenticationService memberAuthenticationService) {
        this.memberAuthenticationService = memberAuthenticationService;
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        String normalizedEmail = email == null ? "" : email.trim();
        MemberAuthenticationView member =
                memberAuthenticationService.findForAuthentication(normalizedEmail)
                .orElseThrow(() -> new UsernameNotFoundException("이메일 또는 비밀번호가 올바르지 않습니다."));

        if (!member.loginAllowed()) {
            // 계정 존재 여부나 탈퇴 상태를 외부에 구분해 노출하지 않는다.
            throw new UsernameNotFoundException("이메일 또는 비밀번호가 올바르지 않습니다.");
        }

        return new MemberDetails(member);
    }
}
