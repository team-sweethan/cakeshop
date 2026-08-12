package com.cakeshop.global.security;

import com.cakeshop.domain.member.dto.view.MemberAuthenticationView;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.stereotype.Component;

/** 회원 인증 결과를 현재 HTTP 세션에 안전하게 반영하는 Spring Security 어댑터다. */
@Component
public class MemberAuthenticationSession {

    private final SecurityContextRepository securityContextRepository =
            new HttpSessionSecurityContextRepository();
    private final SessionRegistry sessionRegistry;

    public MemberAuthenticationSession(SessionRegistry sessionRegistry) {
        this.sessionRegistry = sessionRegistry;
    }

    public void login(
            HttpServletRequest request,
            HttpServletResponse response,
            MemberAuthenticationView member
    ) {
        MemberDetails memberDetails = new MemberDetails(member);
        UsernamePasswordAuthenticationToken authentication =
                UsernamePasswordAuthenticationToken.authenticated(
                        memberDetails,
                        null,
                        memberDetails.getAuthorities());
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);
        HttpSession session = request.getSession(true);
        sessionRegistry.registerNewSession(session.getId(), memberDetails);
    }

    public void clear(HttpServletRequest request, HttpServletResponse response) {
        SecurityContext emptyContext = SecurityContextHolder.createEmptyContext();
        SecurityContextHolder.setContext(emptyContext);
        securityContextRepository.saveContext(emptyContext, request, response);
        HttpSession session = request.getSession(false);
        if (session != null) {
            sessionRegistry.removeSessionInformation(session.getId());
        }
    }
}
