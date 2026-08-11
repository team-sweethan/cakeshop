package com.cakeshop.domain.member.controller;

import com.cakeshop.domain.member.dto.form.EmailRecoveryForm;
import com.cakeshop.domain.member.dto.form.SignupForm;
import com.cakeshop.domain.member.dto.view.EmailAvailabilityView;
import com.cakeshop.domain.member.dto.view.EmailRecoveryResult;
import com.cakeshop.domain.member.error.MemberErrorCode;
import com.cakeshop.domain.member.service.MemberService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
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

    private static final String RECOVERED_EMAILS_SESSION_KEY = "recoveredEmails";
    private static final Duration RECOVERY_SESSION_TTL = Duration.ofMinutes(5);

    private final MemberService memberService;

    // 로그인 화면
    @GetMapping("/login")
    public String login() {
        return "auth/login";
    }

    // 관리자 로그인 화면
    @GetMapping("/admin/login")
    public String adminLogin() {
        return "auth/admin-login";
    }

    // 회원가입 화면
    @GetMapping("/signup")
    public String signup(Model model) {
        model.addAttribute("signupForm", new SignupForm());
        return "customer/member/signup";
    }

    // 이메일 찾기 화면
    @GetMapping("/find-email")
    public String findEmail(Model model, HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.removeAttribute(RECOVERED_EMAILS_SESSION_KEY);
        }
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
            Model model,
            HttpSession session) {
        session.removeAttribute(RECOVERED_EMAILS_SESSION_KEY);
        model.addAttribute("recoveredEmails", List.of());
        model.addAttribute("searched", false);
        if (bindingResult.hasErrors()) {
            return "customer/member/find-email";
        }

        EmailRecoveryResult result =
                memberService.findEmails(form.getName(), form.getBirthDate(), form.getPhone());
        String recoveryToken = UUID.randomUUID().toString();
        session.setAttribute(
                RECOVERED_EMAILS_SESSION_KEY,
                new EmailRecoverySession(
                        result.emails(),
                        recoveryToken,
                        Instant.now().plus(RECOVERY_SESSION_TTL)));
        model.addAttribute("recoveredEmails", result.views());
        model.addAttribute("recoveryToken", recoveryToken);
        model.addAttribute("searched", true);
        return "customer/member/find-email";
    }

    // 찾은 이메일로 로그인 화면 이동
    @PostMapping("/find-email/login")
    public String loginWithRecoveredEmail(
            @RequestParam int selectedIndex,
            @RequestParam String recoveryToken,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        EmailRecoverySession recoveredEmailSession =
                (EmailRecoverySession) session.getAttribute(RECOVERED_EMAILS_SESSION_KEY);
        session.removeAttribute(RECOVERED_EMAILS_SESSION_KEY);
        if (recoveredEmailSession == null
                || !recoveredEmailSession.canSelect(recoveryToken, selectedIndex, Instant.now())) {
            redirectAttributes.addFlashAttribute(
                    "errorMessage",
                    "이메일을 다시 찾아 주세요.");
            return "redirect:/find-email";
        }

        redirectAttributes.addFlashAttribute(
                "recoveredEmail",
                recoveredEmailSession.emails().get(selectedIndex));
        return "redirect:/login";
    }

    // 회원가입 처리
    @PostMapping("/join")
    public String join(@Valid @ModelAttribute("signupForm") SignupForm form,
                       BindingResult bindingResult,
                       HttpSession session,
                       Model model,
                       RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            restoreSignupEmailVerification(form, session, model);
            return "customer/member/signup";
        }

        if (memberService.checkEmailDuplicate(form.getEmail())) {
            bindingResult.rejectValue(
                    "email",
                    MemberErrorCode.DUPLICATE_EMAIL.code(),
                    MemberErrorCode.DUPLICATE_EMAIL.message());
            return "customer/member/signup";
        }

        String verifiedEmail = (String) session.getAttribute(
                EmailVerificationController.SIGNUP_VERIFIED_EMAIL_SESSION_KEY);
        if (!memberService.join(form, verifiedEmail)) {
            bindingResult.rejectValue(
                    "email",
                    MemberErrorCode.EMAIL_VERIFICATION_REQUIRED.code(),
                    MemberErrorCode.EMAIL_VERIFICATION_REQUIRED.message());
            return "customer/member/signup";
        }
        session.removeAttribute(EmailVerificationController.SIGNUP_VERIFIED_EMAIL_SESSION_KEY);
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

    private void restoreSignupEmailVerification(
            SignupForm form,
            HttpSession session,
            Model model) {
        String verifiedEmail = (String) session.getAttribute(
                EmailVerificationController.SIGNUP_VERIFIED_EMAIL_SESSION_KEY);
        if (form.getEmail() != null
                && form.getEmail().trim().equalsIgnoreCase(verifiedEmail)) {
            model.addAttribute("signupEmailVerified", true);
        }
    }
}
