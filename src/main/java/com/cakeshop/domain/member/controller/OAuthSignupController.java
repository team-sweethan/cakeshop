package com.cakeshop.domain.member.controller;

import com.cakeshop.domain.member.dto.form.OAuthSignupForm;
import com.cakeshop.domain.member.dto.view.OAuthSignupSession;
import com.cakeshop.domain.member.service.SocialLoginService;
import com.cakeshop.global.security.MemberAuthenticationSession;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequiredArgsConstructor
public class OAuthSignupController {

    private final SocialLoginService socialLoginService;
    private final MemberAuthenticationSession memberAuthenticationSession;

    // 소셜 회원가입 추가 정보 입력 화면
    @GetMapping("/oauth/signup")
    public String signup(Model model, HttpSession session, RedirectAttributes redirectAttributes) {
        OAuthSignupSession oauthSession = getValidSession(session);
        if (oauthSession == null) {
            redirectAttributes.addFlashAttribute(
                    "errorMessage",
                    "소셜 로그인 인증 시간이 만료되었습니다. 다시 로그인해 주세요.");
            return "redirect:/login";
        }

        OAuthSignupForm form = new OAuthSignupForm();
        form.setName(oauthSession.identity().name());
        model.addAttribute("oauthSignupForm", form);
        model.addAttribute("oauthEmail", oauthSession.identity().email());
        return "auth/oauth-signup";
    }

    // 소셜 회원가입 추가 정보 저장
    @PostMapping("/oauth/signup")
    public String signup(
            @Valid @ModelAttribute("oauthSignupForm") OAuthSignupForm form,
            BindingResult bindingResult,
            HttpSession session,
            HttpServletRequest request,
            HttpServletResponse response,
            Model model,
            RedirectAttributes redirectAttributes
    ) {
        OAuthSignupSession oauthSession = getValidSession(session);
        if (oauthSession == null) {
            redirectAttributes.addFlashAttribute(
                    "errorMessage",
                    "소셜 로그인 인증 시간이 만료되었습니다. 다시 로그인해 주세요.");
            return "redirect:/login";
        }
        if (bindingResult.hasErrors()) {
            model.addAttribute("oauthEmail", oauthSession.identity().email());
            return "auth/oauth-signup";
        }

        memberAuthenticationSession.login(
                request,
                response,
                socialLoginService.signup(oauthSession.identity(), form));
        session.removeAttribute(OAuthSignupSession.SESSION_KEY);
        redirectAttributes.addFlashAttribute("successMessage", "소셜 회원가입이 완료되었습니다.");
        return "redirect:/";
    }

    private OAuthSignupSession getValidSession(HttpSession session) {
        OAuthSignupSession oauthSession = (OAuthSignupSession) session.getAttribute(
                OAuthSignupSession.SESSION_KEY);
        if (oauthSession == null || oauthSession.isExpired(Instant.now())) {
            session.removeAttribute(OAuthSignupSession.SESSION_KEY);
            return null;
        }
        return oauthSession;
    }
}
