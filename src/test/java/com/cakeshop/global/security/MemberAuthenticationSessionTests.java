package com.cakeshop.global.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import com.cakeshop.domain.member.dto.view.MemberAuthenticationView;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.session.SessionRegistry;

class MemberAuthenticationSessionTests {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void login_passwordlessSocialMember_createsMemberDetailsAndRegistersSession() {
        SessionRegistry sessionRegistry = org.mockito.Mockito.mock(SessionRegistry.class);
        MemberAuthenticationSession authenticationSession =
                new MemberAuthenticationSession(sessionRegistry);
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        MemberAuthenticationView socialMember = new MemberAuthenticationView(
                7L, "social@example.com", null, "USER", true, "소셜회원");

        authenticationSession.login(request, response, socialMember);

        assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal())
                .isInstanceOf(MemberDetails.class);
        MemberDetails principal = (MemberDetails) SecurityContextHolder.getContext()
                .getAuthentication()
                .getPrincipal();
        assertThat(principal.getPassword()).isNotBlank();
        assertThat(principal.getUsername()).isEqualTo("social@example.com");
        verify(sessionRegistry).registerNewSession(
                eq(request.getSession().getId()), any(MemberDetails.class));
    }
}
