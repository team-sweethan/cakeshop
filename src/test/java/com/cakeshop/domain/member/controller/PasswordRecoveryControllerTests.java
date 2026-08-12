package com.cakeshop.domain.member.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
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

import com.cakeshop.domain.member.dto.view.PasswordResetResult;
import com.cakeshop.domain.member.service.MemberService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.savedrequest.DefaultSavedRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class PasswordRecoveryControllerTests {

    private MemberService memberService;
    private SessionRegistry sessionRegistry;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        memberService = mock(MemberService.class);
        sessionRegistry = mock(SessionRegistry.class);
        mockMvc = MockMvcBuilders.standaloneSetup(
                new PasswordRecoveryController(memberService, sessionRegistry)).build();
    }

    @Test
    void findPassword_newRequest_clearsPreviousRecoverySession() throws Exception {
        MockHttpSession session = validRecoverySession();

        mockMvc.perform(get("/find-password").session(session))
                .andExpect(status().isOk())
                .andExpect(view().name("customer/member/find-password"));

        assertThat(session.getAttribute(
                PasswordRecoveryController.PASSWORD_RECOVERY_SESSION_KEY)).isNull();
    }

    @Test
    void resetPassword_expiredSession_redirectsToVerification() throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(
                PasswordRecoveryController.PASSWORD_RECOVERY_SESSION_KEY,
                new PasswordRecoverySession(
                        11L, 7L, "member@example.com", Instant.now().minusSeconds(1)));

        mockMvc.perform(get("/reset-password").session(session))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/find-password"));
    }

    @Test
    void resetPassword_withoutSession_doesNotCreateSession() throws Exception {
        org.springframework.test.web.servlet.MvcResult result =
                mockMvc.perform(get("/reset-password"))
                        .andExpect(status().is3xxRedirection())
                        .andExpect(redirectedUrl("/find-password"))
                        .andReturn();

        assertThat(result.getRequest().getSession(false)).isNull();
    }

    @Test
    void resetPassword_invalidNewPassword_keepsRecoverySession() throws Exception {
        MockHttpSession session = validRecoverySession();

        mockMvc.perform(post("/reset-password")
                        .session(session)
                        .param("newPassword", "password")
                        .param("newPasswordConfirm", "different"))
                .andExpect(status().isOk())
                .andExpect(view().name("customer/member/reset-password"))
                .andExpect(model().attributeHasFieldErrors("passwordForm", "newPassword"));

        assertThat(session.getAttribute(
                PasswordRecoveryController.PASSWORD_RECOVERY_SESSION_KEY)).isNotNull();
        verify(memberService, never()).resetPassword(
                anyLong(), anyLong(), anyString(), anyString());
    }

    @Test
    void resetPassword_validRequest_updatesPasswordExpiresSessionsAndConsumesGrant()
            throws Exception {
        MockHttpSession session = validRecoverySession();
        UserDetails principal = mock(UserDetails.class);
        SessionInformation sessionInformation = mock(SessionInformation.class);
        when(principal.getUsername()).thenReturn("member@example.com");
        when(sessionRegistry.getAllPrincipals()).thenReturn(List.of(principal));
        when(sessionRegistry.getAllSessions(principal, false))
                .thenReturn(List.of(sessionInformation));
        when(memberService.resetPassword(
                11L, 7L, "member@example.com", "NewPassword1!"))
                .thenReturn(PasswordResetResult.SUCCESS);
        session.setAttribute(
                "SPRING_SECURITY_SAVED_REQUEST",
                mock(DefaultSavedRequest.class));

        mockMvc.perform(post("/reset-password")
                        .session(session)
                        .param("newPassword", "NewPassword1!")
                        .param("newPasswordConfirm", "NewPassword1!"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));

        verify(sessionInformation).expireNow();
        assertThat(session.getAttribute(
                PasswordRecoveryController.PASSWORD_RECOVERY_SESSION_KEY)).isNull();
        assertThat(session.getAttribute("SPRING_SECURITY_SAVED_REQUEST")).isNull();
    }

    @Test
    void resetPassword_sameAsCurrentPassword_rendersFieldErrorAndKeepsGrant()
            throws Exception {
        MockHttpSession session = validRecoverySession();
        when(memberService.resetPassword(
                11L, 7L, "member@example.com", "CurrentPassword1!"))
                .thenReturn(PasswordResetResult.SAME_AS_CURRENT);

        mockMvc.perform(post("/reset-password")
                        .session(session)
                        .param("newPassword", "CurrentPassword1!")
                        .param("newPasswordConfirm", "CurrentPassword1!"))
                .andExpect(status().isOk())
                .andExpect(view().name("customer/member/reset-password"))
                .andExpect(model().attributeHasFieldErrors("passwordForm", "newPassword"));

        assertThat(session.getAttribute(
                PasswordRecoveryController.PASSWORD_RECOVERY_SESSION_KEY)).isNotNull();
    }

    private MockHttpSession validRecoverySession() {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(
                PasswordRecoveryController.PASSWORD_RECOVERY_SESSION_KEY,
                new PasswordRecoverySession(
                        11L, 7L, "member@example.com", Instant.now().plusSeconds(300)));
        return session;
    }
}
