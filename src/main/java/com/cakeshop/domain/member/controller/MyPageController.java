package com.cakeshop.domain.member.controller;

import com.cakeshop.domain.member.dto.form.ProfileUpdateForm;
import com.cakeshop.domain.member.dto.view.MemberProfileView;
import com.cakeshop.domain.member.error.MemberErrorCode;
import com.cakeshop.domain.member.service.MemberService;
import com.cakeshop.global.error.BusinessException;
import com.cakeshop.global.security.MemberDetails;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

@Controller
@RequiredArgsConstructor
public class MyPageController {

    private final MemberService memberService;
    private final SessionRegistry sessionRegistry;

    // local public-preview에서 인증 없이 목업 화면을 확인할 때 null 인증 분기를 사용한다.
    // 운영 환경에서는 SecurityConfig가 미인증 접근을 차단한다.
    @GetMapping("/mypage")
    public String myPage(
            @AuthenticationPrincipal MemberDetails memberDetails,
            Model model) {

        if (memberDetails == null) {
            model.addAttribute("member", new MemberProfileView("", "", "", "", null));
            return "customer/member/mypage";
        }

        String email = memberDetails.getUsername();
        model.addAttribute("member", memberService.getMemberProfile(email));

        return "customer/member/mypage";
    }

    @GetMapping("/mypage/profile")
    public String profile(
            @AuthenticationPrincipal MemberDetails memberDetails,
            Model model) {

        if (memberDetails == null) {
            MemberProfileView mock =
                    new MemberProfileView(
                            "hong@test.com",
                            "홍길동",
                            "케이크러버",
                            "010-1234-5678",
                            null);
            model.addAttribute("profileForm", ProfileUpdateForm.from(mock));
            return "customer/member/profile-edit";
        }

        String email = memberDetails.getUsername();
        MemberProfileView member = memberService.getMemberProfile(email);

        model.addAttribute("profileForm", ProfileUpdateForm.from(member));

        return "customer/member/profile-edit";
    }


    // 3. 회원정보 수정 처리
    @PostMapping("/mypage/profile/update")
    public String updateProfile(
            @AuthenticationPrincipal MemberDetails memberDetails,
            @Valid @ModelAttribute("profileForm") ProfileUpdateForm form,
            BindingResult bindingResult,
            Model model) {

        if (memberDetails == null) {
            return "redirect:/login";
        }

        String email = memberDetails.getUsername();
        if (bindingResult.hasErrors()) {
            // 읽기 전용 이메일은 요청값을 신뢰하지 않고 인증된 회원 정보로 되돌린다.
            form.setEmail(memberService.getMemberProfile(email).email());
            return "customer/member/profile-edit";
        }

        try {
            memberService.updateMemberInfo(email, form);
        } catch (BusinessException exception) {
            if (exception.getErrorCode() != MemberErrorCode.INVALID_CURRENT_PASSWORD) {
                throw exception;
            }
            bindingResult.rejectValue(
                    "currentPassword",
                    MemberErrorCode.INVALID_CURRENT_PASSWORD.code(),
                    MemberErrorCode.INVALID_CURRENT_PASSWORD.message());
            form.setEmail(memberService.getMemberProfile(email).email());
            return "customer/member/profile-edit";
        }
        return "redirect:/mypage?success=update";
    }

    @PostMapping("/mypage/withdraw")
    public String withdraw(
            @AuthenticationPrincipal MemberDetails memberDetails,
            HttpServletRequest request) {
        if (memberDetails == null) {
            return "redirect:/login";
        }

        memberService.withdraw(memberDetails.getUsername());

        sessionRegistry.getAllSessions(memberDetails, false)
                .forEach(SessionInformation::expireNow);

        // 탈퇴 직후 현재 요청의 세션도 즉시 종료한다.
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
        return "redirect:/login?withdrawn";
    }
}
