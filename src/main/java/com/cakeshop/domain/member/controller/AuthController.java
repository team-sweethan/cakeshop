package com.cakeshop.domain.member.controller;

import com.cakeshop.domain.member.dto.form.EmailRecoveryForm;
import com.cakeshop.domain.member.dto.form.SignupForm;
import com.cakeshop.domain.member.dto.view.EmailAvailabilityView;
import com.cakeshop.domain.member.error.MemberErrorCode;
import com.cakeshop.domain.member.service.MemberService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequiredArgsConstructor
public class AuthController {

    private final MemberService memberService;

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

    @GetMapping("/find-email")
    public String findEmail(Model model) {
        model.addAttribute("emailRecoveryForm", new EmailRecoveryForm());
        return "customer/member/find-email";
    }

    @PostMapping("/find-email")
    public String findEmail(
            @Valid @ModelAttribute("emailRecoveryForm") EmailRecoveryForm form,
            BindingResult bindingResult,
            Model model) {
        if (bindingResult.hasErrors()) {
            return "customer/member/find-email";
        }

        model.addAttribute(
                "maskedEmails",
                memberService.findMaskedEmails(form.getName(), form.getBirthDate(), form.getPhone()));
        model.addAttribute("searched", true);
        return "customer/member/find-email";
    }

    // 회원가입 처리
    @PostMapping("/join")
    public String join(@Valid @ModelAttribute("signupForm") SignupForm form,
                       BindingResult bindingResult,
                       RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            return "customer/member/signup";
        }

        if (memberService.checkEmailDuplicate(form.getEmail())) {
            bindingResult.rejectValue(
                    "email",
                    MemberErrorCode.DUPLICATE_EMAIL.code(),
                    MemberErrorCode.DUPLICATE_EMAIL.message());
            return "customer/member/signup";
        }

        memberService.join(form);
        redirectAttributes.addFlashAttribute("successMessage", "회원가입이 완료되었습니다!");
        return "redirect:/login";
    }

    /**
     * 이메일 중복확인 (AJAX)
     */
    @GetMapping("/emailCheck")
    @ResponseBody
    public EmailAvailabilityView emailCheck(@RequestParam String email) {
        if (!SignupForm.isEmailFormatValid(email)) {
            return new EmailAvailabilityView(
                    false,
                    false,
                    "이메일 형식을 확인해 주세요.");
        }

        boolean available = !memberService.checkEmailDuplicate(email);
        return new EmailAvailabilityView(
                true,
                available,
                available
                        ? "사용 가능한 이메일입니다."
                        : "이미 사용 중인 이메일입니다.");
    }
}
