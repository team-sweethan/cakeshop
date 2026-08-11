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
import com.cakeshop.domain.member.service.EmailVerificationService;
import com.cakeshop.global.error.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.mock.web.MockHttpSession;

class EmailVerificationControllerTests {

    private EmailVerificationService emailVerificationService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        emailVerificationService = org.mockito.Mockito.mock(EmailVerificationService.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new EmailVerificationController(emailVerificationService))
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
                .thenReturn("member@example.com");
        MockHttpSession session = new MockHttpSession();

        mockMvc.perform(post("/email-verifications/signup/verify")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"member@example.com\",\"code\":\"123456\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        assertThat(session.getAttribute(
                EmailVerificationController.SIGNUP_VERIFIED_EMAIL_SESSION_KEY))
                .isEqualTo("member@example.com");
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
}
