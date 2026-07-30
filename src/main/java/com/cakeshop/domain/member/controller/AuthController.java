package com.cakeshop.domain.member.controller;

import com.cakeshop.domain.member.dto.form.EmailRecoveryForm;
import com.cakeshop.domain.member.dto.form.SignupForm;
import com.cakeshop.domain.member.dto.view.EmailAvailabilityView;
import com.cakeshop.domain.member.error.MemberErrorCode;
import com.cakeshop.domain.member.service.MemberService;
import jakarta.validation.Valid;
import java.util.List;
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

    // 이메일 찾기 화면
    @GetMapping("/find-email")
    public String findEmail(Model model) {
        model.addAttribute("emailRecoveryForm", new EmailRecoveryForm());
        model.addAttribute("recoveredEmails", List.of());
        model.addAttribute("searched", false);
        return "customer/member/find-email";
    }

    // 이메일 찾기 처리
    @PostMapping("/find-email")
    public String findEmail(
            @Valid @ModelAttribute("emailRecoveryForm") EmailRecoveryForm form,
            BindingResult bindingResult,
            Model model) {
        model.addAttribute("recoveredEmails", List.of());
        model.addAttribute("searched", false);
        if (bindingResult.hasErrors()) {
            return "customer/member/find-email";
        }

        model.addAttribute(
                "recoveredEmails",
                memberService.findEmails(form.getName(), form.getBirthDate(), form.getPhone()));
        model.addAttribute("searched", true);
        return "customer/member/find-email";
    }

    // 찾은 이메일로 로그인 화면 이동
    @PostMapping("/find-email/login")
    public String loginWithRecoveredEmail(
            @RequestParam String selectedEmail,
            RedirectAttributes redirectAttributes) {
        redirectAttributes.addFlashAttribute("recoveredEmail", selectedEmail);
        return "redirect:/login";
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
