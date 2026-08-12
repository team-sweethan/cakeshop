package com.cakeshop.domain.member.controller;

import com.cakeshop.domain.member.dto.form.OAuthSignupForm;
import com.cakeshop.domain.member.dto.view.OAuthSignupSession;
import com.cakeshop.domain.member.service.SocialLoginService;
import com.cakeshop.global.security.MemberAuthenticationSession;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import java.io.IOException;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.support.RequestContextUtils;
import org.springframework.web.servlet.support.SessionFlashMapManager;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;

@Controller
@RequiredArgsConstructor
public class OAuthSignupController {

    private final SocialLoginService socialLoginService;
    private final MemberAuthenticationSession memberAuthenticationSession;
    private final AuthenticationSuccessHandler customerSuccessHandler = successHandler();

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
        addOAuthIdentity(model, oauthSession);
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
    ) throws IOException, ServletException {
        OAuthSignupSession oauthSession = getValidSession(session);
        if (oauthSession == null) {
            redirectAttributes.addFlashAttribute(
                    "errorMessage",
                    "소셜 로그인 인증 시간이 만료되었습니다. 다시 로그인해 주세요.");
            return "redirect:/login";
        }
        if (bindingResult.hasErrors()) {
            addOAuthIdentity(model, oauthSession);
            return "auth/oauth-signup";
        }

        request.changeSessionId();
        memberAuthenticationSession.login(
                request,
                response,
                socialLoginService.signup(oauthSession.identity(), form));
        session.removeAttribute(OAuthSignupSession.SESSION_KEY);
        addSuccessMessage(request, response);
        customerSuccessHandler.onAuthenticationSuccess(
                request,
                response,
                SecurityContextHolder.getContext().getAuthentication());
        return null;
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

    private void addOAuthIdentity(Model model, OAuthSignupSession oauthSession) {
        model.addAttribute("oauthEmail", oauthSession.identity().email());
        model.addAttribute("oauthProviderName", providerName(oauthSession.identity().provider()));
    }

    private String providerName(String provider) {
        return "KAKAO".equalsIgnoreCase(provider) ? "카카오" : "Google";
    }

    private AuthenticationSuccessHandler successHandler() {
        SavedRequestAwareAuthenticationSuccessHandler handler =
                new SavedRequestAwareAuthenticationSuccessHandler();
        handler.setDefaultTargetUrl("/");
        return handler;
    }

    private void addSuccessMessage(HttpServletRequest request, HttpServletResponse response) {
        RequestContextUtils.getOutputFlashMap(request)
                .put("successMessage", "소셜 회원가입이 완료되었습니다.");
        new SessionFlashMapManager().saveOutputFlashMap(
                RequestContextUtils.getOutputFlashMap(request), request, response);
    }
}
