package com.cakeshop.global.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;

/** 로그인 실패 시 요청한 고객·관리자 로그인 화면으로 돌아간다. */
public class PortalAuthenticationFailureHandler implements AuthenticationFailureHandler {

    private final AuthenticationFailureHandler adminFailureHandler =
            new SimpleUrlAuthenticationFailureHandler("/admin/login?error");
    private final AuthenticationFailureHandler customerFailureHandler =
            new SimpleUrlAuthenticationFailureHandler("/login?error");

    @Override
    public void onAuthenticationFailure(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException exception
    ) throws IOException, ServletException {
        AuthenticationFailureHandler failureHandler =
                "admin".equals(request.getParameter("loginType"))
                        ? adminFailureHandler
                        : customerFailureHandler;
        failureHandler.onAuthenticationFailure(request, response, exception);
    }
}
