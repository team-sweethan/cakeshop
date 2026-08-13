package com.cakeshop.domain.member.dto.view;

/** OAuth 제공자가 검증해 전달한 최소 식별 정보다. */
public record OAuthIdentity(
        String provider,
        String providerId,
        String email,
        String name
) {
}
