package com.cakeshop.global.security;

import java.util.List;

import com.cakeshop.domain.member.dto.view.MemberAuthenticationView;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;

public class MemberDetails extends User {

    private final Long memberId;

    public MemberDetails(MemberAuthenticationView member) {
        super(member.email(), member.password(),
                List.of(new SimpleGrantedAuthority("ROLE_" + member.role())));

        this.memberId = member.id();
    }

    public Long getMemberId() {
        return memberId;
    }

    public boolean isAdmin() {
        return getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
    }
}
