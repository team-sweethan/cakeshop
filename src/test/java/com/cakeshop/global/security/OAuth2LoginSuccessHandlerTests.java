package com.cakeshop.global.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cakeshop.domain.member.dto.view.MemberAuthenticationView;
import com.cakeshop.domain.member.dto.view.OAuthLoginResult;
import com.cakeshop.domain.member.service.SocialLoginService;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;

class OAuth2LoginSuccessHandlerTests {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void existingSocialMember_returnsToSavedRequest() throws Exception {
        SocialLoginService socialLoginService = Mockito.mock(SocialLoginService.class);
        MemberAuthenticationSession memberAuthenticationSession =
                Mockito.mock(MemberAuthenticationSession.class);
        OAuth2LoginSuccessHandler handler = new OAuth2LoginSuccessHandler(
                socialLoginService, memberAuthenticationSession);
        MemberAuthenticationView member = new MemberAuthenticationView(
                7L, "social@cakeshop.local", null, "USER", true, "소셜회원");
        when(socialLoginService.resolveLogin(any())).thenReturn(OAuthLoginResult.login(member));

        MockHttpSession session = new MockHttpSession();
        MockHttpServletRequest savedRequest = new MockHttpServletRequest("GET", "/orders/checkout");
        savedRequest.setSession(session);
        new HttpSessionRequestCache().saveRequest(savedRequest, new MockHttpServletResponse());

        OAuth2AuthenticationToken authentication = googleAuthentication();
        SecurityContextHolder.getContext().setAuthentication(authentication);
        MockHttpServletRequest callbackRequest = new MockHttpServletRequest("GET", "/login/oauth2/code/google");
        callbackRequest.setSession(session);
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationSuccess(callbackRequest, response, authentication);

        assertThat(response.getRedirectedUrl())
                .startsWith("http://localhost/orders/checkout");
        verify(memberAuthenticationSession).login(callbackRequest, response, member);
    }

    private OAuth2AuthenticationToken googleAuthentication() {
        OAuth2User user = Mockito.mock(OAuth2User.class);
        when(user.getAttribute("sub")).thenReturn("google-subject");
        when(user.getAttribute("email")).thenReturn("social@cakeshop.local");
        when(user.getAttribute("email_verified")).thenReturn(true);
        when(user.getAttribute("name")).thenReturn("소셜회원");
        return new OAuth2AuthenticationToken(user, List.of(), "google");
    }
}
