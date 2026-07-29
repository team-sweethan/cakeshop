package com.cakeshop.domain.member.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.cakeshop.domain.member.dto.form.ProfileUpdateForm;
import com.cakeshop.domain.member.dto.view.MemberAuthenticationView;
import com.cakeshop.domain.member.service.MemberService;
import com.cakeshop.global.security.MemberDetails;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;

@ExtendWith(MockitoExtension.class)
class MyPageControllerTests {

    @Mock
    private MemberService memberService;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpSession session;

    @Mock
    private BindingResult bindingResult;

    @Mock
    private Model model;

    @InjectMocks
    private MyPageController myPageController;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void withdraw_authenticatedMember_invalidatesSessionAndClearsSecurityContext() {
        MemberDetails memberDetails = memberDetails();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        memberDetails,
                        null,
                        memberDetails.getAuthorities()));
        when(request.getSession(false)).thenReturn(session);

        String viewName = myPageController.withdraw(memberDetails, request);

        assertThat(viewName).isEqualTo("redirect:/login?withdrawn");
        assertThat(SecurityContextHolder.getContext().getAuthentication())
                .isNull();
        verify(memberService).withdraw(memberDetails.getUsername());
        verify(session).invalidate();
    }

    @Test
    void updateProfile_unauthenticatedMember_redirectsWithoutUpdating() {
        String viewName = myPageController.updateProfile(
                null,
                new ProfileUpdateForm(),
                bindingResult,
                model);

        assertThat(viewName).isEqualTo("redirect:/login");
        verifyNoInteractions(memberService);
    }

    @Test
    void withdraw_unauthenticatedMember_redirectsWithoutWithdrawing() {
        String viewName = myPageController.withdraw(null, request);

        assertThat(viewName).isEqualTo("redirect:/login");
        verifyNoInteractions(memberService, request);
    }

    private MemberDetails memberDetails() {
        return new MemberDetails(new MemberAuthenticationView(
                1L,
                "member@cakeshop.local",
                "encoded-password",
                "USER",
                true));
    }
}
