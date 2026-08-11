package com.cakeshop.global.security;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cakeshop.domain.member.controller.AuthController;
import com.cakeshop.domain.member.dto.view.MemberAuthenticationView;
import com.cakeshop.domain.member.service.MemberService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, PortalLoginSecurityTests.AuthenticationTestConfig.class})
class PortalLoginSecurityTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockitoBean
    private MemberService memberService;

    @MockitoBean
    private MemberDetailsService memberDetailsService;

    @Test
    void adminPortal_adminAccount_redirectsToAdmin() throws Exception {
        stubMember("admin@example.com", "ADMIN");

        mockMvc.perform(post("/login")
                        .with(csrf())
                        .param("loginType", "admin")
                        .param("email", "admin@example.com")
                        .param("password", "Password1!"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin"))
                .andExpect(authenticated().withRoles("ADMIN"));
    }

    @Test
    void adminDashboard_anonymousUser_redirectsToAdminLogin() throws Exception {
        mockMvc.perform(get("/admin"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/login"));
    }

    @Test
    void customerPortal_customerAccount_redirectsToHome() throws Exception {
        stubMember("user@example.com", "USER");

        mockMvc.perform(post("/login")
                        .with(csrf())
                        .param("loginType", "customer")
                        .param("email", "user@example.com")
                        .param("password", "Password1!"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"))
                .andExpect(authenticated().withRoles("USER"));
    }

    @Test
    void adminPortal_customerAccount_rejectsAndClearsAuthentication() throws Exception {
        stubMember("user@example.com", "USER");

        mockMvc.perform(post("/login")
                        .with(csrf())
                        .param("loginType", "admin")
                        .param("email", "user@example.com")
                        .param("password", "Password1!"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/login?error"))
                .andExpect(unauthenticated());
    }

    @Test
    void customerPortal_adminAccount_rejectsAndClearsAuthentication() throws Exception {
        stubMember("admin@example.com", "ADMIN");

        mockMvc.perform(post("/login")
                        .with(csrf())
                        .param("loginType", "customer")
                        .param("email", "admin@example.com")
                        .param("password", "Password1!"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?error"))
                .andExpect(unauthenticated());
    }

    @Test
    void adminPortal_wrongPassword_returnsToAdminLogin() throws Exception {
        stubMember("admin@example.com", "ADMIN");

        mockMvc.perform(post("/login")
                        .with(csrf())
                        .param("loginType", "admin")
                        .param("email", "admin@example.com")
                        .param("password", "wrong-password"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/login?error"))
                .andExpect(unauthenticated());
    }

    @Test
    void customerPage_adminAccount_redirectsToAdmin() throws Exception {
        mockMvc.perform(get("/mypage")
                        .with(user("admin@example.com").roles("ADMIN")))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin"));
    }

    @Test
    void customerCommand_adminAccount_isForbidden() throws Exception {
        mockMvc.perform(post("/cart/items")
                        .with(user("admin@example.com").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }

    private void stubMember(String email, String role) {
        when(memberDetailsService.loadUserByUsername(email))
                .thenReturn(new MemberDetails(new MemberAuthenticationView(
                        1L,
                        email,
                        passwordEncoder.encode("Password1!"),
                        role,
                        true
                )));
    }

    @TestConfiguration
    static class AuthenticationTestConfig {

        @Bean
        AuthenticationProvider testAuthenticationProvider(
                MemberDetailsService memberDetailsService,
                PasswordEncoder passwordEncoder
        ) {
            DaoAuthenticationProvider provider =
                    new DaoAuthenticationProvider(memberDetailsService);
            provider.setPasswordEncoder(passwordEncoder);
            return provider;
        }
    }
}
