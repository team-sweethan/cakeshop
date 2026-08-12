package com.cakeshop.domain.member.dto.view;

/** OAuth 인증 뒤 회원 상태에 따라 결정한 다음 단계다. */
public record OAuthLoginResult(
        Type type,
        MemberAuthenticationView member
) {

    public enum Type {
        LOGIN,
        SIGNUP_REQUIRED,
        EXISTING_EMAIL,
        UNAVAILABLE
    }

    public static OAuthLoginResult login(MemberAuthenticationView member) {
        return new OAuthLoginResult(Type.LOGIN, member);
    }

    public static OAuthLoginResult of(Type type) {
        return new OAuthLoginResult(type, null);
    }
}
