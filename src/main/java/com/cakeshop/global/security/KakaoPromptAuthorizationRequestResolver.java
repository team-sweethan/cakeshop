package com.cakeshop.global.security;

import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;

/** 카카오 계정 재선택 요청에 prompt=login을 전달하는 OAuth 어댑터다. */
public class KakaoPromptAuthorizationRequestResolver implements OAuth2AuthorizationRequestResolver {

    private final OAuth2AuthorizationRequestResolver delegate;

    public KakaoPromptAuthorizationRequestResolver(
            ClientRegistrationRepository clientRegistrationRepository) {
        this.delegate = new DefaultOAuth2AuthorizationRequestResolver(
                clientRegistrationRepository,
                "/oauth2/authorization");
    }

    @Override
    public OAuth2AuthorizationRequest resolve(HttpServletRequest request) {
        return customize(request, delegate.resolve(request));
    }

    @Override
    public OAuth2AuthorizationRequest resolve(HttpServletRequest request, String clientRegistrationId) {
        return customize(request, delegate.resolve(request, clientRegistrationId));
    }

    private OAuth2AuthorizationRequest customize(
            HttpServletRequest request,
            OAuth2AuthorizationRequest authorizationRequest) {
        if (authorizationRequest == null
                || !"kakao".equals(authorizationRequest.getAttribute(
                OAuth2ParameterNames.REGISTRATION_ID))
                || !"login".equals(request.getParameter("prompt"))) {
            return authorizationRequest;
        }

        Map<String, Object> additionalParameters = new LinkedHashMap<>(
                authorizationRequest.getAdditionalParameters());
        additionalParameters.put("prompt", "login");
        return OAuth2AuthorizationRequest.from(authorizationRequest)
                .additionalParameters(additionalParameters)
                .build();
    }
}
