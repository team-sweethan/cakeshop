package com.cakeshop.global.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;

class OAuth2AccountSelectionRequestResolverTests {

    private final OAuth2AccountSelectionRequestResolver resolver =
            new OAuth2AccountSelectionRequestResolver(
                    new InMemoryClientRegistrationRepository(List.of(
                            googleRegistration(),
                            kakaoRegistration(),
                            unsupportedRegistration())));

    @ParameterizedTest
    @ValueSource(strings = {"google", "kakao"})
    void selectAccountPrompt_supportedProvider_addsPrompt(String registrationId) {
        MockHttpServletRequest request = request(registrationId);
        request.setParameter("prompt", "select_account");

        OAuth2AuthorizationRequest authorizationRequest = resolver.resolve(request);

        assertThat(authorizationRequest.getAdditionalParameters())
                .containsEntry("prompt", "select_account");
    }

    @ParameterizedTest
    @ValueSource(strings = {"google", "kakao"})
    void regularLogin_supportedProvider_doesNotForcePrompt(String registrationId) {
        OAuth2AuthorizationRequest authorizationRequest = resolver.resolve(
                request(registrationId));

        assertThat(authorizationRequest.getAdditionalParameters())
                .doesNotContainKey("prompt");
    }

    @Test
    void unsupportedPrompt_doesNotAddPrompt() {
        MockHttpServletRequest request = request("google");
        request.setParameter("prompt", "login");

        OAuth2AuthorizationRequest authorizationRequest = resolver.resolve(request);

        assertThat(authorizationRequest.getAdditionalParameters())
                .doesNotContainKey("prompt");
    }

    @Test
    void selectAccountPrompt_unsupportedProvider_doesNotAddPrompt() {
        MockHttpServletRequest request = request("unsupported");
        request.setParameter("prompt", "select_account");

        OAuth2AuthorizationRequest authorizationRequest = resolver.resolve(request);

        assertThat(authorizationRequest.getAdditionalParameters())
                .doesNotContainKey("prompt");
    }

    private static MockHttpServletRequest request(String registrationId) {
        return new MockHttpServletRequest(
                "GET", "/oauth2/authorization/" + registrationId);
    }

    private static ClientRegistration googleRegistration() {
        return ClientRegistration.withRegistrationId("google")
                .clientId("google-client-id")
                .clientSecret("google-client-secret")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .authorizationUri("https://accounts.google.com/o/oauth2/v2/auth")
                .tokenUri("https://oauth2.googleapis.com/token")
                .userInfoUri("https://openidconnect.googleapis.com/v1/userinfo")
                .userNameAttributeName("sub")
                .scope("openid", "profile", "email")
                .build();
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

    private static ClientRegistration unsupportedRegistration() {
        return ClientRegistration.withRegistrationId("unsupported")
                .clientId("unsupported-client-id")
                .clientSecret("unsupported-client-secret")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .authorizationUri("https://unsupported.example/authorize")
                .tokenUri("https://unsupported.example/token")
                .userInfoUri("https://unsupported.example/userinfo")
                .userNameAttributeName("id")
                .build();
    }
}
