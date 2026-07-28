package com.cakeshop.domain.member.controller;

import com.cakeshop.domain.member.dto.form.SignupForm;
import com.cakeshop.domain.member.service.MemberService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class AuthController {

    @Autowired
    private MemberService memberService;

    // 로그인 화면
    @GetMapping("/login")
    public String login() {
        return "auth/login";
    }

    // 회원가입 화면
    @GetMapping("/signup")
    public String signup(Model model) {
        model.addAttribute("signupForm", new SignupForm());
        return "customer/member/signup";
    }

    // 회원가입 처리
    @PostMapping("/join")
    public String join(SignupForm form,
                       RedirectAttributes redirectAttributes) {

        try {
            memberService.join(form);

            // 로그인 페이지에서 사용할 성공 메시지
            redirectAttributes.addFlashAttribute("successMsg", "회원가입이 완료되었습니다!");

            return "redirect:/login";

        } catch (Exception e) {

            redirectAttributes.addFlashAttribute("msg", e.getMessage());

            return "redirect:/signup";
        }
    }

    /**
     * 이메일 중복확인 (AJAX)
     */
    @GetMapping("/emailCheck")
    @ResponseBody
    public boolean emailCheck(@RequestParam String email) {
        return memberService.checkEmailDuplicate(email);
    }
}