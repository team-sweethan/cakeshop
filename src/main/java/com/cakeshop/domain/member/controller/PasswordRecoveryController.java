package com.cakeshop.domain.member.controller;

import com.cakeshop.domain.member.dto.form.PasswordForm;
import com.cakeshop.domain.member.dto.view.PasswordResetResult;
import com.cakeshop.domain.member.service.MemberService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import java.time.Instant;
import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class PasswordRecoveryController {

    static final String PASSWORD_RECOVERY_SESSION_KEY = "passwordRecovery";
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
        return "customer/member/find-password";
    }

    // 비밀번호 재설정 화면
    @GetMapping("/reset-password")
    public String resetPassword(
            Model model,
            HttpServletRequest request,
            RedirectAttributes redirectAttributes) {
        HttpSession session = request.getSession(false);
        PasswordRecoverySession recoverySession = getValidRecoverySession(session);
        if (recoverySession == null) {
            removeRecoverySession(session);
            addInvalidRecoveryMessage(session, redirectAttributes);
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
            HttpServletRequest request,
            HttpServletResponse response,
            RedirectAttributes redirectAttributes) {
        HttpSession session = request.getSession(false);
        PasswordRecoverySession recoverySession = getValidRecoverySession(session);
        if (recoverySession == null) {
            removeRecoverySession(session);
            addInvalidRecoveryMessage(session, redirectAttributes);
            return "redirect:/find-password";
        }
        if (bindingResult.hasErrors()) {
            return "customer/member/reset-password";
        }

        PasswordResetResult resetResult =
                memberService.resetPassword(
                        recoverySession.verificationId(),
                        recoverySession.memberId(),
                        recoverySession.email(),
                        form.getNewPassword());
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
        new HttpSessionRequestCache().removeRequest(request, response);
        redirectAttributes.addFlashAttribute(
                "successMessage",
                "비밀번호가 재설정되었습니다. 새 비밀번호로 로그인해 주세요.");
        return "redirect:/login";
    }

    private PasswordRecoverySession getValidRecoverySession(HttpSession session) {
        if (session == null) {
            return null;
        }
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

    private void addInvalidRecoveryMessage(
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        if (session != null) {
            redirectAttributes.addFlashAttribute(
                    "errorMessage",
                    "회원정보를 다시 확인해 주세요.");
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
