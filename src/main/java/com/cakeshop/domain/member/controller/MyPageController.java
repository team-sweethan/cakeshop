package com.cakeshop.domain.member.controller;

import com.cakeshop.domain.member.dto.form.ProfileUpdateForm;
import com.cakeshop.domain.member.entity.Member;
import com.cakeshop.domain.member.service.MemberService;
import com.cakeshop.global.security.MemberDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

@Controller
@RequiredArgsConstructor
public class MyPageController {

    private final MemberService memberService;

    // 이제 이 컨트롤러가 /mypage 요청을 전담합니다.
    @GetMapping("/mypage")
    public String myPage(@AuthenticationPrincipal MemberDetails memberDetails, Model model) {
        // 1. 인증 객체에서 이메일 추출
        String email = memberDetails.getUsername();

        // 2. 이메일로 DB에서 최신 회원 정보 조회 (서비스 활용)
        Member freshMember = memberService.getMemberByEmail(email);

        // 3. 최신 데이터를 모델에 담아서 전달
        model.addAttribute("member", freshMember);

        return "customer/member/mypage";
    }

    @GetMapping("/mypage/profile")
    public String profile(@AuthenticationPrincipal MemberDetails memberDetails, Model model) {
        // 로그인한 멤버 정보를 모델에 넣어줘야 타임리프가 인식합니다.
        model.addAttribute("member", memberDetails.getMember());
        return "customer/member/profile-edit";
    }


    // 3. 회원정보 수정 처리
    @PostMapping("/mypage/profile/update")
    public String updateProfile(
            @AuthenticationPrincipal MemberDetails memberDetails,
            @ModelAttribute ProfileUpdateForm form,
            Model model) {

        try {
            // 세션(인증 객체)에서 이메일을 추출하여 서비스로 전달
            String email = memberDetails.getUsername();
            memberService.updateMemberInfo(email, form);

            return "redirect:/mypage?success=update";
        } catch (IllegalArgumentException e) {
            // 에러 발생 시 에러 메시지와 함께 폼 유지
            model.addAttribute("error", e.getMessage());
            model.addAttribute("member", memberDetails.getMember());
            return "customer/member/profile-edit";
        } catch (Exception e) {
            model.addAttribute("error", "서버 오류가 발생했습니다.");
            model.addAttribute("member", memberDetails.getMember());
            return "customer/member/profile-edit";
        }
    }
}