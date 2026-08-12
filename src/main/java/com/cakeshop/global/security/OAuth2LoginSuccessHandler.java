package com.cakeshop.global.security;

import com.cakeshop.domain.member.dto.form.SignupForm;
import com.cakeshop.domain.member.dto.view.OAuthIdentity;
import com.cakeshop.domain.member.dto.view.OAuthLoginResult;
import com.cakeshop.domain.member.dto.view.OAuthSignupSession;
import com.cakeshop.domain.member.service.SocialLoginService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.DefaultRedirectStrategy;
import org.springframework.security.web.RedirectStrategy;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** Google OAuth 인증 결과를 기존 회원 로그인 또는 추가 정보 입력으로 분기한다. */
@Component
@Profile("oauth")
@RequiredArgsConstructor
public class OAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {

    private static final Duration SIGNUP_SESSION_TTL = Duration.ofMinutes(5);

    private final SocialLoginService socialLoginService;
    private final MemberAuthenticationSession memberAuthenticationSession;
    private final RedirectStrategy redirectStrategy = new DefaultRedirectStrategy();
    private final AuthenticationSuccessHandler customerSuccessHandler = successHandler();

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication
    ) throws IOException, ServletException {
        OAuthIdentity identity = extractGoogleIdentity(authentication);
        if (identity == null) {
            memberAuthenticationSession.clear(request, response);
            redirectStrategy.sendRedirect(request, response, "/login?oauthError");
            return;
        }

        OAuthLoginResult result = socialLoginService.resolveLogin(identity);
        switch (result.type()) {
            case LOGIN -> {
                memberAuthenticationSession.login(request, response, result.member());
                customerSuccessHandler.onAuthenticationSuccess(
                        request,
                        response,
                        org.springframework.security.core.context.SecurityContextHolder
                                .getContext()
                                .getAuthentication());
            }
            case SIGNUP_REQUIRED -> {
                memberAuthenticationSession.clear(request, response);
                request.getSession(true).setAttribute(
                        OAuthSignupSession.SESSION_KEY,
                        new OAuthSignupSession(
                                identity,
                                Instant.now().plus(SIGNUP_SESSION_TTL)));
                redirectStrategy.sendRedirect(request, response, "/oauth/signup");
            }
            case EXISTING_EMAIL -> {
                memberAuthenticationSession.clear(request, response);
                redirectStrategy.sendRedirect(request, response, "/login?oauthAccountExists");
            }
            case UNAVAILABLE -> {
                memberAuthenticationSession.clear(request, response);
                redirectStrategy.sendRedirect(request, response, "/login?oauthError");
            }
        }
    }

    private OAuthIdentity extractGoogleIdentity(Authentication authentication) {
        if (!(authentication instanceof OAuth2AuthenticationToken oauthToken)
                || !"google".equalsIgnoreCase(oauthToken.getAuthorizedClientRegistrationId())) {
            return null;
        }

        OAuth2User user = oauthToken.getPrincipal();
        String providerId = user.getAttribute("sub");
        String email = user.getAttribute("email");
        Boolean emailVerified = user.getAttribute("email_verified");
        if (isBlank(providerId) || !Boolean.TRUE.equals(emailVerified)
                || !SignupForm.isEmailFormatValid(email)) {
            return null;
        }
        String name = user.getAttribute("name");
        return new OAuthIdentity(
                "GOOGLE",
                providerId.trim(),
                email.trim().toLowerCase(Locale.ROOT),
                name == null ? "" : name.trim());
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private AuthenticationSuccessHandler successHandler() {
        SavedRequestAwareAuthenticationSuccessHandler handler =
                new SavedRequestAwareAuthenticationSuccessHandler();
        handler.setDefaultTargetUrl("/");
        return handler;
    }
}
