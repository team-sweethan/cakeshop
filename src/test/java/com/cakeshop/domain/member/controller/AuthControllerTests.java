package com.cakeshop.domain.member.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.cakeshop.domain.member.dto.form.SignupForm;
import com.cakeshop.domain.member.service.MemberService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AuthControllerTests {

    private MemberService memberService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        memberService = org.mockito.Mockito.mock(MemberService.class);
        AuthController controller = new AuthController(memberService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void join_invalidSignupInput_rendersFormWithoutCallingService() throws Exception {
        mockMvc.perform(post("/join")
                        .param("email", "invalid-email")
                        .param("password", "password")
                        .param("passwordConfirm", "different")
                        .param("name", " ")
                        .param("nickname", " ")
                        .param("phone", "1234"))
                .andExpect(status().isOk())
                .andExpect(view().name("customer/member/signup"))
                .andExpect(model().attributeHasFieldErrors(
                        "signupForm", "email", "password", "name", "nickname", "phone"));

        verify(memberService, never()).join(org.mockito.ArgumentMatchers.any(SignupForm.class));
    }

    @Test
    void join_validSignupInput_redirectsWithCommonSuccessMessage() throws Exception {
        mockMvc.perform(post("/join")
                        .param("email", "member@example.com")
                        .param("password", "Password1!")
                        .param("passwordConfirm", "Password1!")
                        .param("name", "홍길동")
                        .param("nickname", "길동이")
                        .param("phone", "010-1234-5678"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"))
                .andExpect(flash().attribute("successMessage", "회원가입이 완료되었습니다!"));

        verify(memberService).join(org.mockito.ArgumentMatchers.any(SignupForm.class));
    }

    @Test
    void join_duplicateEmail_rendersFormWithEmailErrorAndPreservesInput() throws Exception {
        when(memberService.checkEmailDuplicate("member@example.com"))
                .thenReturn(true);

        MvcResult result = mockMvc.perform(post("/join")
                        .param("email", "member@example.com")
                        .param("password", "Password1!")
                        .param("passwordConfirm", "Password1!")
                        .param("name", "홍길동")
                        .param("nickname", "길동이")
                        .param("phone", "010-1234-5678"))
                .andExpect(status().isOk())
                .andExpect(view().name("customer/member/signup"))
                .andExpect(model().attributeHasFieldErrors("signupForm", "email"))
                .andReturn();

        SignupForm returnedForm = (SignupForm) result.getModelAndView()
                .getModel()
                .get("signupForm");

        assertThat(returnedForm.getEmail()).isEqualTo("member@example.com");
        assertThat(returnedForm.getName()).isEqualTo("홍길동");
        assertThat(returnedForm.getNickname()).isEqualTo("길동이");
        assertThat(returnedForm.getPhone()).isEqualTo("010-1234-5678");
        verify(memberService, never()).join(
                org.mockito.ArgumentMatchers.any(SignupForm.class));
    }

    @Test
    void emailCheck_invalidEmail_returnsFormatErrorWithoutQueryingMember() throws Exception {
        mockMvc.perform(get("/emailCheck")
                        .param("email", "member@domain"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(false))
                .andExpect(jsonPath("$.available").value(false))
                .andExpect(jsonPath("$.message")
                        .value("이메일 형식을 확인해 주세요."));

        verify(memberService, never()).checkEmailDuplicate(
                org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void emailCheck_availableEmail_returnsAvailableResult() throws Exception {
        when(memberService.checkEmailDuplicate("new@example.com"))
                .thenReturn(false);

        mockMvc.perform(get("/emailCheck")
                        .param("email", "new@example.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true))
                .andExpect(jsonPath("$.available").value(true))
                .andExpect(jsonPath("$.message")
                        .value("사용 가능한 이메일입니다."));
    }
}
