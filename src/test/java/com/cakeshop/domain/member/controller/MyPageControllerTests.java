package com.cakeshop.domain.member.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.cakeshop.domain.member.dto.form.ProfileUpdateForm;
import com.cakeshop.domain.member.dto.view.MemberAuthenticationView;
import com.cakeshop.domain.member.dto.view.MemberProfileView;
import com.cakeshop.domain.member.error.MemberErrorCode;
import com.cakeshop.domain.member.service.MemberService;
import com.cakeshop.global.error.BusinessException;
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
import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistry;
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
    private SessionRegistry sessionRegistry;

    @Mock
    private SessionInformation currentSessionInformation;

    @Mock
    private SessionInformation otherSessionInformation;

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
        when(sessionRegistry.getAllSessions(memberDetails, false))
                .thenReturn(java.util.List.of(
                        currentSessionInformation,
                        otherSessionInformation));

        String viewName = myPageController.withdraw(memberDetails, request);

        assertThat(viewName).isEqualTo("redirect:/login?withdrawn");
        assertThat(SecurityContextHolder.getContext().getAuthentication())
                .isNull();
        verify(memberService).withdraw(memberDetails.getUsername());
        verify(currentSessionInformation).expireNow();
        verify(otherSessionInformation).expireNow();
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
    void updateProfile_invalidCurrentPassword_returnsFormWithFieldError() {
        MemberDetails memberDetails = memberDetails();
        ProfileUpdateForm form = new ProfileUpdateForm();
        form.setEmail("untrusted@request.local");
        when(bindingResult.hasErrors()).thenReturn(false);
        doThrow(new BusinessException(MemberErrorCode.INVALID_CURRENT_PASSWORD))
                .when(memberService)
                .updateMemberInfo(memberDetails.getUsername(), form);
        when(memberService.getMemberProfile(memberDetails.getUsername()))
                .thenReturn(new MemberProfileView(
                        memberDetails.getUsername(),
                        "회원",
                        "닉네임",
                        "010-1234-5678"));

        String viewName = myPageController.updateProfile(
                memberDetails,
                form,
                bindingResult,
                model);

        assertThat(viewName).isEqualTo("customer/member/profile-edit");
        assertThat(form.getEmail()).isEqualTo(memberDetails.getUsername());
        verify(bindingResult).rejectValue(
                "currentPassword",
                MemberErrorCode.INVALID_CURRENT_PASSWORD.code(),
                MemberErrorCode.INVALID_CURRENT_PASSWORD.message());
    }

    @Test
    void updateProfile_unexpectedBusinessException_delegatesToGlobalHandler() {
        MemberDetails memberDetails = memberDetails();
        ProfileUpdateForm form = new ProfileUpdateForm();
        when(bindingResult.hasErrors()).thenReturn(false);
        BusinessException exception =
                new BusinessException(MemberErrorCode.UPDATE_FAILED);
        doThrow(exception)
                .when(memberService)
                .updateMemberInfo(memberDetails.getUsername(), form);

        assertThatThrownBy(() -> myPageController.updateProfile(
                memberDetails,
                form,
                bindingResult,
                model))
                .isSameAs(exception);
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
