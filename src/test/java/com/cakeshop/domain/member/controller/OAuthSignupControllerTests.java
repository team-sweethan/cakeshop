package com.cakeshop.domain.member.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.cakeshop.domain.member.dto.form.OAuthSignupForm;
import com.cakeshop.domain.member.dto.view.MemberAuthenticationView;
import com.cakeshop.domain.member.dto.view.OAuthIdentity;
import com.cakeshop.domain.member.dto.view.OAuthSignupSession;
import com.cakeshop.domain.member.service.SocialLoginService;
import com.cakeshop.global.security.MemberAuthenticationSession;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class OAuthSignupControllerTests {

    private SocialLoginService socialLoginService;
    private MemberAuthenticationSession memberAuthenticationSession;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        socialLoginService = org.mockito.Mockito.mock(SocialLoginService.class);
        memberAuthenticationSession = org.mockito.Mockito.mock(MemberAuthenticationSession.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new OAuthSignupController(
                socialLoginService, memberAuthenticationSession)).build();
    }

    @Test
    void signupPage_validOAuthSession_prefillsGoogleNameAndShowsReadonlyEmail() throws Exception {
        MockHttpSession session = signupSession();

        mockMvc.perform(get("/oauth/signup").session(session))
                .andExpect(status().isOk())
                .andExpect(view().name("auth/oauth-signup"))
                .andExpect(model().attribute("oauthEmail", "member@example.com"))
                .andExpect(model().attributeExists("oauthSignupForm"));
    }

    @Test
    void signupPage_expiredOAuthSession_redirectsToLogin() throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(OAuthSignupSession.SESSION_KEY, new OAuthSignupSession(
                new OAuthIdentity("GOOGLE", "subject", "member@example.com", "홍길동"),
                Instant.now().minusSeconds(1)));

        mockMvc.perform(get("/oauth/signup").session(session))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"))
                .andExpect(flash().attributeExists("errorMessage"));
    }

    @Test
    void signup_invalidInput_rendersFormWithoutPersisting() throws Exception {
        mockMvc.perform(post("/oauth/signup")
                        .session(signupSession())
                        .param("name", " ")
                        .param("nickname", " ")
                        .param("phone", "1234"))
                .andExpect(status().isOk())
                .andExpect(view().name("auth/oauth-signup"))
                .andExpect(model().attributeHasFieldErrors(
                        "oauthSignupForm", "name", "nickname", "phone", "birthDate"));

        verify(socialLoginService, never()).signup(any(), any());
    }

    @Test
    void signup_validInput_authenticatesAndRedirectsHome() throws Exception {
        MockHttpSession session = signupSession();
        String previousSessionId = session.getId();
        MemberAuthenticationView member = new MemberAuthenticationView(
                7L, "member@example.com", null, "USER", true, "케이크러버");
        when(socialLoginService.signup(any(), any(OAuthSignupForm.class))).thenReturn(member);

        MvcResult result = mockMvc.perform(post("/oauth/signup")
                        .session(session)
                        .param("name", "홍길동")
                        .param("nickname", "케이크러버")
                        .param("phone", "010-1234-5678")
                        .param("birthDate", "2000-01-15"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"))
                .andExpect(flash().attribute("successMessage", "소셜 회원가입이 완료되었습니다."))
                .andReturn();

        verify(memberAuthenticationSession).login(any(), any(), eq(member));
        verify(socialLoginService).signup(any(), any(OAuthSignupForm.class));
        assertThat(session.getAttribute(OAuthSignupSession.SESSION_KEY)).isNull();
        assertThat(result.getRequest().getSession().getId()).isNotEqualTo(previousSessionId);
    }

    private MockHttpSession signupSession() {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(OAuthSignupSession.SESSION_KEY, new OAuthSignupSession(
                new OAuthIdentity("GOOGLE", "subject", "member@example.com", "홍길동"),
                Instant.now().plusSeconds(300)));
        return session;
    }
}
