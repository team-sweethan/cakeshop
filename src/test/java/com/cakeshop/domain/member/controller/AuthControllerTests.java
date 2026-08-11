package com.cakeshop.domain.member.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.cakeshop.domain.member.dto.form.EmailRecoveryForm;
import com.cakeshop.domain.member.dto.form.SignupForm;
import com.cakeshop.domain.member.dto.view.EmailRecoveryResult;
import com.cakeshop.domain.member.dto.view.RecoveredEmailView;
import com.cakeshop.domain.member.service.MemberService;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
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
    void adminLogin_rendersAdminLoginView() throws Exception {
        mockMvc.perform(get("/admin/login"))
                .andExpect(status().isOk())
                .andExpect(view().name("auth/admin-login"));
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
                        "signupForm", "email", "password", "name", "nickname", "phone", "birthDate"));

        verify(memberService, never()).join(any(SignupForm.class), any());
    }

    @Test
    void join_validSignupInput_redirectsWithCommonSuccessMessage() throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(
                EmailVerificationController.SIGNUP_VERIFIED_EMAIL_SESSION_KEY,
                "member@example.com");
        when(memberService.join(any(SignupForm.class), eq("member@example.com")))
                .thenReturn(true);

        mockMvc.perform(post("/join")
                        .session(session)
                        .param("email", "member@example.com")
                        .param("password", "Password1!")
                        .param("passwordConfirm", "Password1!")
                        .param("name", "홍길동")
                        .param("nickname", "길동이")
                        .param("phone", "010-1234-5678")
                        .param("birthDate", "2000-01-15"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"))
                .andExpect(flash().attribute("successMessage", "회원가입이 완료되었습니다!"));

        verify(memberService).join(any(SignupForm.class), eq("member@example.com"));
        assertThat(session.getAttribute(
                EmailVerificationController.SIGNUP_VERIFIED_EMAIL_SESSION_KEY)).isNull();
    }

    @Test
    void join_expiredEmailVerification_rendersSignupWithEmailError() throws Exception {
        when(memberService.join(any(SignupForm.class), any())).thenReturn(false);

        mockMvc.perform(post("/join")
                        .param("email", "member@example.com")
                        .param("password", "Password1!")
                        .param("passwordConfirm", "Password1!")
                        .param("name", "홍길동")
                        .param("nickname", "길동이")
                        .param("phone", "010-1234-5678")
                        .param("birthDate", "2000-01-15"))
                .andExpect(status().isOk())
                .andExpect(view().name("customer/member/signup"))
                .andExpect(model().attributeHasFieldErrors("signupForm", "email"));
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
                        .param("phone", "010-1234-5678")
                        .param("birthDate", "2000-01-15"))
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
        assertThat(returnedForm.getBirthDate()).isEqualTo(LocalDate.of(2000, 1, 15));
        verify(memberService, never()).join(any(SignupForm.class), any());
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

    @Test
    void findEmail_get_rendersRecoveryForm() throws Exception {
        mockMvc.perform(get("/find-email"))
                .andExpect(status().isOk())
                .andExpect(view().name("customer/member/find-email"))
                .andExpect(model().attributeExists("emailRecoveryForm"));
    }

    @Test
    void findEmail_invalidInput_rendersErrorsWithoutCallingService() throws Exception {
        mockMvc.perform(post("/find-email")
                        .param("name", " ")
                        .param("birthDate", LocalDate.now().plusDays(1).toString())
                        .param("phone", "1234"))
                .andExpect(status().isOk())
                .andExpect(view().name("customer/member/find-email"))
                .andExpect(model().attributeHasFieldErrors(
                        "emailRecoveryForm", "name", "birthDate", "phone"));

        verify(memberService, never()).findEmails(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(LocalDate.class),
                org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void findEmail_matchingMember_rendersMaskedEmailList() throws Exception {
        LocalDate birthDate = LocalDate.of(2000, 1, 15);
        RecoveredEmailView recoveredEmail =
                new RecoveredEmailView(0, "me****@example.com");
        when(memberService.findEmails("홍길동", birthDate, "010-1234-5678"))
                .thenReturn(new EmailRecoveryResult(
                        List.of("member@example.com"),
                        List.of(recoveredEmail)));

        MvcResult result = mockMvc.perform(post("/find-email")
                        .param("name", "홍길동")
                        .param("birthDate", birthDate.toString())
                        .param("phone", "010-1234-5678"))
                .andExpect(status().isOk())
                .andExpect(view().name("customer/member/find-email"))
                .andExpect(model().attribute("searched", true))
                .andExpect(model().attribute("recoveredEmails", List.of(recoveredEmail)))
                .andReturn();

        assertThat(result.getResponse().getContentAsString())
                .doesNotContain("member@example.com");
    }

    @Test
    void loginWithRecoveredEmail_selectedIndex_redirectsWithFlashAttribute() throws Exception {
        LocalDate birthDate = LocalDate.of(2000, 1, 15);
        when(memberService.findEmails("홍길동", birthDate, "010-1234-5678"))
                .thenReturn(new EmailRecoveryResult(
                        List.of("member@example.com"),
                        List.of(new RecoveredEmailView(0, "me****@example.com"))));
        MvcResult recoveryResult = mockMvc.perform(post("/find-email")
                        .param("name", "홍길동")
                        .param("birthDate", birthDate.toString())
                        .param("phone", "010-1234-5678"))
                .andReturn();
        MockHttpSession session =
                (MockHttpSession) recoveryResult.getRequest().getSession(false);
        String recoveryToken =
                (String) recoveryResult.getModelAndView().getModel().get("recoveryToken");

        mockMvc.perform(post("/find-email/login")
                        .session(session)
                        .param("selectedIndex", "0")
                        .param("recoveryToken", recoveryToken))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"))
                .andExpect(flash().attribute("recoveredEmail", "member@example.com"));

        assertThat(session.getAttribute("recoveredEmails")).isNull();
    }

    @Test
    void loginWithRecoveredEmail_missingRecoverySession_redirectsToRecovery() throws Exception {
        mockMvc.perform(post("/find-email/login")
                        .param("selectedIndex", "0")
                        .param("recoveryToken", "unused-token"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/find-email"))
                .andExpect(flash().attribute("errorMessage", "이메일을 다시 찾아 주세요."));
    }

    @Test
    void loginWithRecoveredEmail_invalidToken_redirectsToRecoveryAndClearsSession() throws Exception {
        LocalDate birthDate = LocalDate.of(2000, 1, 15);
        when(memberService.findEmails("홍길동", birthDate, "010-1234-5678"))
                .thenReturn(new EmailRecoveryResult(
                        List.of("member@example.com"),
                        List.of(new RecoveredEmailView(0, "me****@example.com"))));
        MvcResult recoveryResult = mockMvc.perform(post("/find-email")
                        .param("name", "홍길동")
                        .param("birthDate", birthDate.toString())
                        .param("phone", "010-1234-5678"))
                .andReturn();
        MockHttpSession session =
                (MockHttpSession) recoveryResult.getRequest().getSession(false);

        mockMvc.perform(post("/find-email/login")
                        .session(session)
                        .param("selectedIndex", "0")
                        .param("recoveryToken", "invalid-token"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/find-email"))
                .andExpect(flash().attribute("errorMessage", "이메일을 다시 찾아 주세요."));

        assertThat(session.getAttribute("recoveredEmails")).isNull();
    }

    @Test
    void findEmail_getAfterRecovery_clearsPreviousRecoverySession() throws Exception {
        LocalDate birthDate = LocalDate.of(2000, 1, 15);
        when(memberService.findEmails("홍길동", birthDate, "010-1234-5678"))
                .thenReturn(new EmailRecoveryResult(
                        List.of("member@example.com"),
                        List.of(new RecoveredEmailView(0, "me****@example.com"))));
        MvcResult recoveryResult = mockMvc.perform(post("/find-email")
                        .param("name", "홍길동")
                        .param("birthDate", birthDate.toString())
                        .param("phone", "010-1234-5678"))
                .andReturn();
        MockHttpSession session =
                (MockHttpSession) recoveryResult.getRequest().getSession(false);
        String recoveryToken =
                (String) recoveryResult.getModelAndView().getModel().get("recoveryToken");

        mockMvc.perform(get("/find-email").session(session))
                .andExpect(status().isOk());

        assertThat(session.getAttribute("recoveredEmails")).isNull();
        mockMvc.perform(post("/find-email/login")
                        .session(session)
                        .param("selectedIndex", "0")
                        .param("recoveryToken", recoveryToken))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/find-email"))
                .andExpect(flash().attribute("errorMessage", "이메일을 다시 찾아 주세요."));
    }

    @Test
    void findEmail_invalidSubmissionAfterRecovery_clearsPreviousRecoverySession() throws Exception {
        LocalDate birthDate = LocalDate.of(2000, 1, 15);
        when(memberService.findEmails("홍길동", birthDate, "010-1234-5678"))
                .thenReturn(new EmailRecoveryResult(
                        List.of("member@example.com"),
                        List.of(new RecoveredEmailView(0, "me****@example.com"))));
        MvcResult recoveryResult = mockMvc.perform(post("/find-email")
                        .param("name", "홍길동")
                        .param("birthDate", birthDate.toString())
                        .param("phone", "010-1234-5678"))
                .andReturn();
        MockHttpSession session =
                (MockHttpSession) recoveryResult.getRequest().getSession(false);
        String recoveryToken =
                (String) recoveryResult.getModelAndView().getModel().get("recoveryToken");

        mockMvc.perform(post("/find-email")
                        .session(session)
                        .param("name", " ")
                        .param("birthDate", birthDate.toString())
                        .param("phone", "1234"))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors(
                        "emailRecoveryForm", "name", "phone"));

        assertThat(session.getAttribute("recoveredEmails")).isNull();
        mockMvc.perform(post("/find-email/login")
                        .session(session)
                        .param("selectedIndex", "0")
                        .param("recoveryToken", recoveryToken))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/find-email"))
                .andExpect(flash().attribute("errorMessage", "이메일을 다시 찾아 주세요."));
    }
}
