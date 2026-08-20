package com.cakeshop.domain.member.service;

import com.cakeshop.domain.coupon.service.CouponMemberCommandService;
import com.cakeshop.domain.member.dto.form.OAuthSignupForm;
import com.cakeshop.domain.member.dto.form.SignupForm;
import com.cakeshop.domain.member.dto.view.MemberAuthenticationView;
import com.cakeshop.domain.member.dto.view.OAuthIdentity;
import com.cakeshop.domain.member.dto.view.OAuthLoginResult;
import com.cakeshop.domain.member.entity.Member;
import com.cakeshop.domain.member.entity.SocialAccount;
import com.cakeshop.domain.member.error.MemberErrorCode;
import com.cakeshop.domain.member.mapper.MemberMapper;
import com.cakeshop.domain.member.mapper.SocialAccountMapper;
import com.cakeshop.global.error.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SocialLoginService {

    private final SocialAccountMapper socialAccountMapper;
    private final MemberMapper memberMapper;
    private final MemberAuthenticationService memberAuthenticationService;
    private final CouponMemberCommandService couponMemberCommandService;

    /** OAuth 식별 정보로 로그인 또는 추가 정보 입력 여부를 결정한다. */
    @Transactional(readOnly = true)
    public OAuthLoginResult resolveLogin(OAuthIdentity identity) {
        String email = normalizeEmail(identity.email());
        return socialAccountMapper.findMemberEmail(identity.provider(), identity.providerId())
                .map(memberAuthenticationService::findForAuthentication)
                .map(member -> member.filter(authentication ->
                                authentication.loginAllowed()
                                        && "USER".equals(authentication.role()))
                        .map(OAuthLoginResult::login)
                        .orElseGet(() -> OAuthLoginResult.of(OAuthLoginResult.Type.UNAVAILABLE)))
                .orElseGet(() -> memberMapper.findByEmail(email).isPresent()
                        ? OAuthLoginResult.of(OAuthLoginResult.Type.EXISTING_EMAIL)
                        : OAuthLoginResult.of(OAuthLoginResult.Type.SIGNUP_REQUIRED));
    }

    /** OAuth 추가 정보를 완료한 신규 회원과 소셜 연결 정보를 함께 저장한다. */
    @Transactional
    public MemberAuthenticationView signup(OAuthIdentity identity, OAuthSignupForm form) {
        String email = normalizeEmail(identity.email());
        if (socialAccountMapper.findMemberEmail(identity.provider(), identity.providerId()).isPresent()
                || memberMapper.findByEmail(email).isPresent()) {
            throw new BusinessException(MemberErrorCode.OAUTH_SIGNUP_UNAVAILABLE);
        }
        if (!NicknamePolicy.isAllowed(form.getNickname())) {
            throw new BusinessException(MemberErrorCode.RESERVED_NICKNAME);
        }

        Member member = Member.builder()
                .email(email)
                .password(null)
                .name(form.getName().trim())
                .nickname(form.getNickname())
                .phone(form.getPhone().trim())
                .birthDate(form.getBirthDate())
                .role("USER")
                .build();
        memberMapper.join(member);
        if (member.getId() == null) {
            throw new BusinessException(MemberErrorCode.UPDATE_FAILED);
        }

        SocialAccount socialAccount = SocialAccount.builder()
                .memberId(member.getId())
                .provider(identity.provider())
                .providerId(identity.providerId())
                .socialEmail(email)
                .build();
        if (socialAccountMapper.insert(socialAccount) != 1) {
            throw new BusinessException(MemberErrorCode.UPDATE_FAILED);
        }
        couponMemberCommandService.issueNewMemberCoupons(member.getId());
        return memberAuthenticationService.findForAuthentication(email)
                .orElseThrow(() -> new BusinessException(MemberErrorCode.NOT_FOUND));
    }

    private String normalizeEmail(String email) {
        String normalizedEmail = SignupForm.normalizeEmail(email);
        if (!SignupForm.isEmailFormatValid(normalizedEmail)) {
            throw new BusinessException(MemberErrorCode.OAUTH_SIGNUP_UNAVAILABLE);
        }
        return normalizedEmail;
    }
}
