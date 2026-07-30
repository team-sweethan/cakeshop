package com.cakeshop.domain.member.controller;

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

import com.cakeshop.domain.member.service.MemberService;
import com.cakeshop.domain.member.dto.view.PasswordRecoveryTarget;
import com.cakeshop.domain.member.dto.view.PasswordResetResult;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.userdetails.UserDetails;
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
        PasswordRecoveryController controller =
                new PasswordRecoveryController(memberService, sessionRegistry);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void findPassword_newRequest_clearsPreviousRecoverySession() throws Exception {
        MockHttpSession session = validRecoverySession();

        mockMvc.perform(get("/find-password").session(session))
                .andExpect(status().isOk())
                .andExpect(view().name("customer/member/find-password"))
                .andExpect(model().attributeExists("passwordRecoveryForm"));

        org.assertj.core.api.Assertions.assertThat(
                session.getAttribute(PasswordRecoveryController.PASSWORD_RECOVERY_SESSION_KEY))
                .isNull();
    }

    @Test
    void verifyMember_invalidInput_rendersFieldErrorsWithoutCallingService() throws Exception {
        mockMvc.perform(post("/find-password/verify")
                        .param("email", "invalid")
                        .param("name", " ")
                        .param("birthDate", "2030-01-01")
                        .param("phone", "1234"))
                .andExpect(status().isOk())
                .andExpect(view().name("customer/member/find-password"))
                .andExpect(model().attributeHasFieldErrors(
                        "passwordRecoveryForm", "email", "name", "birthDate", "phone"));

        verify(memberService, never()).findPasswordRecoveryMember(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(LocalDate.class),
                org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void verifyMember_mismatchedInfo_rendersGenericError() throws Exception {
        when(memberService.findPasswordRecoveryMember(
                "member@example.com",
                "홍길동",
                LocalDate.of(2000, 1, 15),
                "010-1234-5678"))
                .thenReturn(Optional.empty());

        mockMvc.perform(validVerificationRequest())
                .andExpect(status().isOk())
                .andExpect(view().name("customer/member/find-password"))
                .andExpect(model().attributeHasErrors("passwordRecoveryForm"));
    }

    @Test
    void verifyMember_matchingInfo_storesRecoverySessionAndRedirects() throws Exception {
        when(memberService.findPasswordRecoveryMember(
                "member@example.com",
                "홍길동",
                LocalDate.of(2000, 1, 15),
                "010-1234-5678"))
                .thenReturn(Optional.of(new PasswordRecoveryTarget(7L, "Member@example.com")));
        MockHttpSession session = new MockHttpSession();

        mockMvc.perform(validVerificationRequest().session(session))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/reset-password"));

        PasswordRecoverySession recoverySession = (PasswordRecoverySession) session.getAttribute(
                PasswordRecoveryController.PASSWORD_RECOVERY_SESSION_KEY);
        org.assertj.core.api.Assertions.assertThat(recoverySession.memberId()).isEqualTo(7L);
        org.assertj.core.api.Assertions.assertThat(recoverySession.email())
                .isEqualTo("Member@example.com");
        org.assertj.core.api.Assertions.assertThat(recoverySession.isValid(Instant.now())).isTrue();
    }

    @Test
    void resetPassword_expiredSession_redirectsToVerification() throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(
                PasswordRecoveryController.PASSWORD_RECOVERY_SESSION_KEY,
                new PasswordRecoverySession(
                        7L,
                        "member@example.com",
                        Instant.now().minusSeconds(1)));

        mockMvc.perform(get("/reset-password").session(session))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/find-password"))
                .andExpect(flash().attribute("errorMessage", "회원정보를 다시 확인해 주세요."));
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

        org.assertj.core.api.Assertions.assertThat(
                session.getAttribute(PasswordRecoveryController.PASSWORD_RECOVERY_SESSION_KEY))
                .isNotNull();
        verify(memberService, never()).resetPassword(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void resetPassword_validRequest_updatesPasswordExpiresSessionsAndConsumesGrant() throws Exception {
        MockHttpSession session = validRecoverySession();
        UserDetails principal = mock(UserDetails.class);
        SessionInformation sessionInformation = mock(SessionInformation.class);
        when(principal.getUsername()).thenReturn("member@example.com");
        when(sessionRegistry.getAllPrincipals()).thenReturn(List.of(principal));
        when(sessionRegistry.getAllSessions(principal, false))
                .thenReturn(List.of(sessionInformation));
        when(memberService.resetPassword(7L, "NewPassword1!"))
                .thenReturn(PasswordResetResult.SUCCESS);

        mockMvc.perform(post("/reset-password")
                        .session(session)
                        .param("newPassword", "NewPassword1!")
                        .param("newPasswordConfirm", "NewPassword1!"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"))
                .andExpect(flash().attribute(
                        "successMessage",
                        "비밀번호가 재설정되었습니다. 새 비밀번호로 로그인해 주세요."));

        verify(sessionInformation).expireNow();
        org.assertj.core.api.Assertions.assertThat(
                session.getAttribute(PasswordRecoveryController.PASSWORD_RECOVERY_SESSION_KEY))
                .isNull();

        mockMvc.perform(post("/reset-password")
                        .session(session)
                        .param("newPassword", "AnotherPassword1!")
                        .param("newPasswordConfirm", "AnotherPassword1!"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/find-password"));
    }

    @Test
    void resetPassword_sameAsCurrentPassword_rendersFieldErrorAndKeepsGrant() throws Exception {
        MockHttpSession session = validRecoverySession();
        when(memberService.resetPassword(7L, "CurrentPassword1!"))
                .thenReturn(PasswordResetResult.SAME_AS_CURRENT);

        mockMvc.perform(post("/reset-password")
                        .session(session)
                        .param("newPassword", "CurrentPassword1!")
                        .param("newPasswordConfirm", "CurrentPassword1!"))
                .andExpect(status().isOk())
                .andExpect(view().name("customer/member/reset-password"))
                .andExpect(model().attributeHasFieldErrors("passwordForm", "newPassword"));

        org.assertj.core.api.Assertions.assertThat(
                session.getAttribute(PasswordRecoveryController.PASSWORD_RECOVERY_SESSION_KEY))
                .isNotNull();
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
            validVerificationRequest() {
        return post("/find-password/verify")
                .param("email", "member@example.com")
                .param("name", "홍길동")
                .param("birthDate", "2000-01-15")
                .param("phone", "010-1234-5678");
    }

    private MockHttpSession validRecoverySession() {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(
                PasswordRecoveryController.PASSWORD_RECOVERY_SESSION_KEY,
                new PasswordRecoverySession(
                        7L,
                        "member@example.com",
                        Instant.now().plusSeconds(300)));
        return session;
    }
}
