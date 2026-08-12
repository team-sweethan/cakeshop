package com.cakeshop.domain.member.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cakeshop.domain.member.error.EmailVerificationExceptionHandler;
import com.cakeshop.domain.member.error.MemberErrorCode;
import com.cakeshop.domain.member.dto.view.SignupEmailVerification;
import com.cakeshop.domain.member.dto.view.PasswordResetEmailVerification;
import com.cakeshop.domain.member.service.EmailVerificationService;
import com.cakeshop.domain.member.service.PasswordResetEmailDispatchService;
import com.cakeshop.global.error.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.mock.web.MockHttpSession;

class EmailVerificationControllerTests {

    private EmailVerificationService emailVerificationService;
    private PasswordResetEmailDispatchService passwordResetEmailDispatchService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        emailVerificationService = org.mockito.Mockito.mock(EmailVerificationService.class);
        passwordResetEmailDispatchService =
                org.mockito.Mockito.mock(PasswordResetEmailDispatchService.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new EmailVerificationController(
                        emailVerificationService,
                        passwordResetEmailDispatchService))
                .setControllerAdvice(new EmailVerificationExceptionHandler())
                .build();
    }

    @Test
    void sendSignupCode_validEmail_returnsSuccess() throws Exception {
        mockMvc.perform(post("/email-verifications/signup/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"member@example.com\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("인증번호를 발송했습니다."));

        verify(emailVerificationService).sendSignupCode("member@example.com");
    }

    @Test
    void verifySignupCode_invalidCode_returnsBadRequest() throws Exception {
        mockMvc.perform(post("/email-verifications/signup/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"member@example.com\",\"code\":\"123\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void verifySignupCode_validCode_bindsVerifiedEmailToSession() throws Exception {
        when(emailVerificationService.verifySignupCode("member@example.com", "123456"))
                .thenReturn(new SignupEmailVerification(7L, "member@example.com"));
        MockHttpSession session = new MockHttpSession();

        mockMvc.perform(post("/email-verifications/signup/verify")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"member@example.com\",\"code\":\"123456\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        assertThat(session.getAttribute(
                EmailVerificationController.SIGNUP_VERIFIED_EMAIL_SESSION_KEY))
                .isEqualTo(new SignupEmailVerification(7L, "member@example.com"));
    }

    @Test
    void sendSignupCode_rateLimited_returnsBusinessErrorJson() throws Exception {
        doThrow(new BusinessException(MemberErrorCode.EMAIL_VERIFICATION_RATE_LIMITED))
                .when(emailVerificationService)
                .sendSignupCode("member@example.com");

        mockMvc.perform(post("/email-verifications/signup/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"member@example.com\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("MEMBER_012"));
    }

    @Test
    void sendPasswordResetCode_returnsGenericSuccess() throws Exception {
        mockMvc.perform(post("/email-verifications/password-reset/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"member@example.com\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message")
                        .value("입력한 이메일로 인증번호 발송을 요청했습니다."));

        verify(passwordResetEmailDispatchService).dispatch("member@example.com");
    }

    @Test
    void verifyPasswordResetCode_validCode_storesBoundRecoverySession() throws Exception {
        when(emailVerificationService.verifyPasswordResetCode(
                "member@example.com", "123456"))
                .thenReturn(new PasswordResetEmailVerification(
                        11L,
                        7L,
                        "member@example.com",
                        java.time.Instant.now().plusSeconds(60)));
        MockHttpSession session = new MockHttpSession();

        mockMvc.perform(post("/email-verifications/password-reset/verify")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"member@example.com\",\"code\":\"123456\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        PasswordRecoverySession recoverySession =
                (PasswordRecoverySession) session.getAttribute(
                        PasswordRecoveryController.PASSWORD_RECOVERY_SESSION_KEY);
        assertThat(recoverySession.verificationId()).isEqualTo(11L);
        assertThat(recoverySession.memberId()).isEqualTo(7L);
        assertThat(recoverySession.email()).isEqualTo("member@example.com");
        assertThat(recoverySession.isValid(java.time.Instant.now())).isTrue();
    }
}
