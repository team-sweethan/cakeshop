package com.cakeshop.global.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.DefaultRedirectStrategy;
import org.springframework.security.web.RedirectStrategy;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;

// 로그인 성공 시 역할에 따라 기본 진입점을 분기한다.
// 보호 페이지 접근 후 로그인한 경우(저장된 요청)에는 원래 가려던 경로를 우선한다.
public class RoleBasedAuthenticationSuccessHandler implements AuthenticationSuccessHandler {

    private static final String ADMIN_LOGIN_TYPE = "admin";
    private static final SecurityContextLogoutHandler LOGOUT_HANDLER =
            new SecurityContextLogoutHandler();
    private final AuthenticationSuccessHandler adminSuccessHandler =
            successHandler("/admin");
    private final AuthenticationSuccessHandler customerSuccessHandler =
            successHandler("/");
    private final RedirectStrategy redirectStrategy = new DefaultRedirectStrategy();

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication)
            throws IOException, ServletException {
        boolean isAdmin = authentication.getAuthorities().stream()
            .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
        boolean adminLogin = ADMIN_LOGIN_TYPE.equals(request.getParameter("loginType"));
        if (isAdmin != adminLogin) {
            LOGOUT_HANDLER.logout(request, response, authentication);
            redirectStrategy.sendRedirect(
                    request,
                    response,
                    adminLogin ? "/admin/login?error" : "/login?error"
            );
            return;
        }
        // 관리자는 대시보드, 회원은 메인으로 보낸다. 저장된 요청이 있으면 원래 경로를 우선한다.
        AuthenticationSuccessHandler successHandler =
                isAdmin ? adminSuccessHandler : customerSuccessHandler;
        successHandler.onAuthenticationSuccess(request, response, authentication);
    }

    private static AuthenticationSuccessHandler successHandler(String defaultTargetUrl) {
        SavedRequestAwareAuthenticationSuccessHandler handler =
                new SavedRequestAwareAuthenticationSuccessHandler();
        handler.setDefaultTargetUrl(defaultTargetUrl);
        return handler;
    }
}
