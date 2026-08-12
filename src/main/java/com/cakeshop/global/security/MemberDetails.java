package com.cakeshop.global.security;

import java.util.List;

import com.cakeshop.domain.member.dto.view.MemberAuthenticationView;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;

public class MemberDetails extends User {

    private static final String OAUTH_SESSION_PASSWORD = "oauth-session-only";

    private final Long memberId;
    private final String displayName;

    public MemberDetails(MemberAuthenticationView member) {
        super(member.email(), resolvePassword(member.password()),
                List.of(new SimpleGrantedAuthority("ROLE_" + member.role())));

        this.memberId = member.id();
        this.displayName = member.displayName();
    }

    public Long getMemberId() {
        return memberId;
    }

    public String getDisplayName() {
        return displayName;
    }

    private static String resolvePassword(String password) {
        return password == null ? OAUTH_SESSION_PASSWORD : password;
    }
}
