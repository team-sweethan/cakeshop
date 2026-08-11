package com.cakeshop.domain.member.dto.view;

/** Spring Security 인증에 필요한 회원 정보만 제공하는 읽기 DTO다. */
public record MemberAuthenticationView(
        Long id,
        String email,
        String password,
        String role,
        boolean loginAllowed,
        String displayName
) {

    public MemberAuthenticationView(
            Long id,
            String email,
            String password,
            String role,
            boolean loginAllowed
    ) {
        this(id, email, password, role, loginAllowed, email);
    }
}
