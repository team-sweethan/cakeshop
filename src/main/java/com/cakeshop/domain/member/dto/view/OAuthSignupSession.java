package com.cakeshop.domain.member.dto.view;

import java.time.Instant;

/** OAuth 인증 뒤 추가 정보 입력 화면에만 보관하는 짧은 세션 값이다. */
public record OAuthSignupSession(
        OAuthIdentity identity,
        Instant expiresAt
) {

    public static final String SESSION_KEY = "oauthSignup";

    public boolean isExpired(Instant now) {
        return !expiresAt.isAfter(now);
    }
}
