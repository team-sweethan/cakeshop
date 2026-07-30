package com.cakeshop.domain.member.controller;

import com.cakeshop.domain.member.dto.form.PasswordForm;
import com.cakeshop.domain.member.dto.form.PasswordRecoveryForm;
import com.cakeshop.domain.member.dto.view.PasswordRecoveryTarget;
import com.cakeshop.domain.member.dto.view.PasswordResetResult;
import com.cakeshop.domain.member.service.MemberService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@ConditionalOnProperty(
        name = "app.member.simple-password-reset-enabled",
        havingValue = "true")
public class PasswordRecoveryController {

    static final String PASSWORD_RECOVERY_SESSION_KEY = "passwordRecovery";
    private static final Duration PASSWORD_RECOVERY_TTL = Duration.ofMinutes(5);

    private final MemberService memberService;
    private final SessionRegistry sessionRegistry;

    public PasswordRecoveryController(
            MemberService memberService,
            SessionRegistry sessionRegistry) {
        this.memberService = memberService;
        this.sessionRegistry = sessionRegistry;
    }

    // 비밀번호 찾기 화면
    @GetMapping("/find-password")
    public String findPassword(Model model, HttpServletRequest request) {
        removeRecoverySession(request.getSession(false));
        model.addAttribute("passwordRecoveryForm", new PasswordRecoveryForm());
        return "customer/member/find-password";
    }

    // 비밀번호 재설정 회원 확인
    @PostMapping("/find-password/verify")
    public String verifyMember(
            @Valid @ModelAttribute("passwordRecoveryForm") PasswordRecoveryForm form,
            BindingResult bindingResult,
            HttpServletRequest request) {
        removeRecoverySession(request.getSession(false));
        if (bindingResult.hasErrors()) {
            return "customer/member/find-password";
        }

        Optional<PasswordRecoveryTarget> target = memberService.findPasswordRecoveryMember(
                form.getEmail(),
                form.getName(),
                form.getBirthDate(),
                form.getPhone());
        if (target.isEmpty()) {
            bindingResult.reject(
                    "passwordRecoveryNotFound",
                    "입력한 정보와 일치하는 회원을 찾을 수 없습니다.");
            return "customer/member/find-password";
        }

        HttpSession session = request.getSession();
        request.changeSessionId();
        session.setAttribute(
                PASSWORD_RECOVERY_SESSION_KEY,
                new PasswordRecoverySession(
                        target.get().memberId(),
                        target.get().email(),
                        Instant.now().plus(PASSWORD_RECOVERY_TTL)));
        return "redirect:/reset-password";
    }

    // 비밀번호 재설정 화면
    @GetMapping("/reset-password")
    public String resetPassword(
            Model model,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        PasswordRecoverySession recoverySession = getValidRecoverySession(session);
        if (recoverySession == null) {
            removeRecoverySession(session);
            redirectAttributes.addFlashAttribute(
                    "errorMessage",
                    "회원정보를 다시 확인해 주세요.");
            return "redirect:/find-password";
        }

        model.addAttribute("passwordForm", new PasswordForm());
        return "customer/member/reset-password";
    }

    // 비밀번호 재설정 처리
    @PostMapping("/reset-password")
    public String resetPassword(
            @Valid @ModelAttribute("passwordForm") PasswordForm form,
            BindingResult bindingResult,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        PasswordRecoverySession recoverySession = getValidRecoverySession(session);
        if (recoverySession == null) {
            removeRecoverySession(session);
            redirectAttributes.addFlashAttribute(
                    "errorMessage",
                    "회원정보를 다시 확인해 주세요.");
            return "redirect:/find-password";
        }
        if (bindingResult.hasErrors()) {
            return "customer/member/reset-password";
        }

        PasswordResetResult resetResult =
                memberService.resetPassword(recoverySession.memberId(), form.getNewPassword());
        if (resetResult == PasswordResetResult.SAME_AS_CURRENT) {
            bindingResult.rejectValue(
                    "newPassword",
                    "sameAsCurrentPassword",
                    "기존 비밀번호는 사용할 수 없습니다.");
            return "customer/member/reset-password";
        }

        removeRecoverySession(session);
        if (resetResult == PasswordResetResult.UNAVAILABLE) {
            redirectAttributes.addFlashAttribute(
                    "errorMessage",
                    "회원정보를 다시 확인해 주세요.");
            return "redirect:/find-password";
        }

        expireExistingSessions(recoverySession.email());
        redirectAttributes.addFlashAttribute(
                "successMessage",
                "비밀번호가 재설정되었습니다. 새 비밀번호로 로그인해 주세요.");
        return "redirect:/login";
    }

    private PasswordRecoverySession getValidRecoverySession(HttpSession session) {
        Object attribute = session.getAttribute(PASSWORD_RECOVERY_SESSION_KEY);
        if (!(attribute instanceof PasswordRecoverySession recoverySession)
                || !recoverySession.isValid(Instant.now())) {
            return null;
        }
        return recoverySession;
    }

    private void removeRecoverySession(HttpSession session) {
        if (session != null) {
            session.removeAttribute(PASSWORD_RECOVERY_SESSION_KEY);
        }
    }

    private void expireExistingSessions(String email) {
        sessionRegistry.getAllPrincipals().stream()
                .filter(UserDetails.class::isInstance)
                .map(UserDetails.class::cast)
                .filter(principal -> email.equals(principal.getUsername()))
                .flatMap(principal -> sessionRegistry.getAllSessions(principal, false).stream())
                .forEach(SessionInformation::expireNow);
    }
}
