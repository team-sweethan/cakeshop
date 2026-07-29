package com.cakeshop.global.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cakeshop.domain.member.controller.AuthController;
import com.cakeshop.domain.member.service.MemberService;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AuthController.class)
@Import(SecurityConfig.class)
class MemberLogoutSecurityTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MemberService memberService;

    @Test
    void logout_withoutCsrf_isForbidden() throws Exception {
        mockMvc.perform(post("/logout").with(user("member@cakeshop.local")))
                .andExpect(status().isForbidden());
    }

    @Test
    void logout_authenticatedMember_invalidatesSessionAndRedirectsToLogin()
            throws Exception {
        MockHttpSession session = new MockHttpSession();

        mockMvc.perform(post("/logout")
                        .session(session)
                        .with(user("member@cakeshop.local"))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/?logout"))
                .andExpect(cookie().maxAge("JSESSIONID", 0))
                .andExpect(unauthenticated());

        assertThat(session.isInvalid()).isTrue();
    }

    @Test
    void myPage_logoutUsesPostForm() throws Exception {
        String template =
                new ClassPathResource("templates/customer/member/mypage.html")
                        .getContentAsString(StandardCharsets.UTF_8);

        assertThat(template)
                .contains("th:action=\"@{/logout}\"")
                .contains("method=\"post\"")
                .doesNotContain("href=\"/logout\"");
    }

    @Test
    void home_logoutUsesCommonPopup() throws Exception {
        String template =
                new ClassPathResource("templates/home/main.html")
                        .getContentAsString(StandardCharsets.UTF_8);

        assertThat(template)
                .contains("fragments/common/alert :: popup");
    }
}
