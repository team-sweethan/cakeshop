package com.cakeshop.domain.member.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cakeshop.domain.coupon.service.CouponMemberCommandService;
import com.cakeshop.domain.member.dto.form.OAuthSignupForm;
import com.cakeshop.domain.member.dto.view.MemberAuthenticationView;
import com.cakeshop.domain.member.dto.view.OAuthIdentity;
import com.cakeshop.domain.member.dto.view.OAuthLoginResult;
import com.cakeshop.domain.member.entity.Member;
import com.cakeshop.domain.member.entity.SocialAccount;
import com.cakeshop.domain.member.mapper.MemberMapper;
import com.cakeshop.domain.member.mapper.SocialAccountMapper;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SocialLoginServiceTests {

    private static final OAuthIdentity GOOGLE_IDENTITY = new OAuthIdentity(
            "GOOGLE", "google-subject", "member@example.com", "홍길동");

    @Mock
    private SocialAccountMapper socialAccountMapper;

    @Mock
    private MemberMapper memberMapper;

    @Mock
    private MemberAuthenticationService memberAuthenticationService;

    @Mock
    private CouponMemberCommandService couponMemberCommandService;

    @InjectMocks
    private SocialLoginService socialLoginService;

    @Test
    void resolveLogin_linkedActiveUser_returnsLogin() {
        MemberAuthenticationView member = new MemberAuthenticationView(
                1L, "member@example.com", null, "USER", true, "케이크러버");
        when(socialAccountMapper.findMemberEmail("GOOGLE", "google-subject"))
                .thenReturn(Optional.of("member@example.com"));
        when(memberAuthenticationService.findForAuthentication("member@example.com"))
                .thenReturn(Optional.of(member));

        OAuthLoginResult result = socialLoginService.resolveLogin(GOOGLE_IDENTITY);

        assertThat(result).isEqualTo(OAuthLoginResult.login(member));
    }

    @Test
    void resolveLogin_existingEmailWithoutSocialLink_requiresRegularLogin() {
        when(socialAccountMapper.findMemberEmail("GOOGLE", "google-subject"))
                .thenReturn(Optional.empty());
        when(memberMapper.findByEmail("member@example.com"))
                .thenReturn(Optional.of(Member.builder().id(1L).build()));

        OAuthLoginResult result = socialLoginService.resolveLogin(GOOGLE_IDENTITY);

        assertThat(result.type()).isEqualTo(OAuthLoginResult.Type.EXISTING_EMAIL);
    }

    @Test
    void signup_persistsPasswordlessMemberAndSocialAccountTogether() {
        OAuthSignupForm form = new OAuthSignupForm();
        form.setName("홍길동");
        form.setNickname("케이크러버");
        form.setPhone("010-1234-5678");
        form.setBirthDate(LocalDate.of(2000, 1, 15));
        MemberAuthenticationView authenticatedMember = new MemberAuthenticationView(
                7L, "member@example.com", null, "USER", true, "케이크러버");
        when(socialAccountMapper.findMemberEmail("GOOGLE", "google-subject"))
                .thenReturn(Optional.empty());
        when(memberMapper.findByEmail("member@example.com")).thenReturn(Optional.empty());
        org.mockito.Mockito.doAnswer(invocation -> {
            invocation.getArgument(0, Member.class).setId(7L);
            return 1;
        }).when(memberMapper).join(any(Member.class));
        when(socialAccountMapper.insert(any(SocialAccount.class))).thenReturn(1);
        when(memberAuthenticationService.findForAuthentication("member@example.com"))
                .thenReturn(Optional.of(authenticatedMember));

        MemberAuthenticationView result = socialLoginService.signup(GOOGLE_IDENTITY, form);

        ArgumentCaptor<Member> memberCaptor = ArgumentCaptor.forClass(Member.class);
        ArgumentCaptor<SocialAccount> accountCaptor = ArgumentCaptor.forClass(SocialAccount.class);
        verify(memberMapper).join(memberCaptor.capture());
        verify(socialAccountMapper).insert(accountCaptor.capture());
        verify(couponMemberCommandService).issueNewMemberCoupons(7L);
        assertThat(memberCaptor.getValue().getPassword()).isNull();
        assertThat(memberCaptor.getValue().getRole()).isEqualTo("USER");
        assertThat(accountCaptor.getValue().getMemberId()).isEqualTo(7L);
        assertThat(accountCaptor.getValue().getProvider()).isEqualTo("GOOGLE");
        assertThat(accountCaptor.getValue().getProviderId()).isEqualTo("google-subject");
        assertThat(result).isEqualTo(authenticatedMember);
    }
}
