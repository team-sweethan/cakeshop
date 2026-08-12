package com.cakeshop.global.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;

class KakaoPromptAuthorizationRequestResolverTests {

    private final KakaoPromptAuthorizationRequestResolver resolver =
            new KakaoPromptAuthorizationRequestResolver(
                    new InMemoryClientRegistrationRepository(kakaoRegistration()));

    @Test
    void kakaoPromptLogin_addsPromptToAuthorizationRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest(
                "GET", "/oauth2/authorization/kakao");
        request.setParameter("prompt", "login");

        OAuth2AuthorizationRequest authorizationRequest = resolver.resolve(request);

        assertThat(authorizationRequest.getAdditionalParameters())
                .containsEntry("prompt", "login");
    }

    @Test
    void regularKakaoLogin_doesNotForcePrompt() {
        MockHttpServletRequest request = new MockHttpServletRequest(
                "GET", "/oauth2/authorization/kakao");

        OAuth2AuthorizationRequest authorizationRequest = resolver.resolve(request);

        assertThat(authorizationRequest.getAdditionalParameters())
                .doesNotContainKey("prompt");
    }

    private static ClientRegistration kakaoRegistration() {
        return ClientRegistration.withRegistrationId("kakao")
                .clientId("kakao-client-id")
                .clientSecret("kakao-client-secret")
                .clientAuthenticationMethod(org.springframework.security.oauth2.core.
                        ClientAuthenticationMethod.CLIENT_SECRET_POST)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .authorizationUri("https://kauth.kakao.com/oauth/authorize")
                .tokenUri("https://kauth.kakao.com/oauth/token")
                .userInfoUri("https://kapi.kakao.com/v2/user/me")
                .userNameAttributeName("id")
                .scope("account_email")
                .build();
    }
}
