package com.cakeshop.global.security;

import jakarta.servlet.http.HttpServletRequest;
import java.net.InetAddress;
import java.net.UnknownHostException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.util.matcher.AndRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.security.web.session.HttpSessionEventPublisher;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final boolean publicPreview;

    public SecurityConfig(@Value("${app.mockup.public-preview:false}") boolean publicPreview) {
        this.publicPreview = publicPreview;
    }

    @Bean
    public SecurityFilterChain filterChain(
            HttpSecurity http,
            SessionRegistry sessionRegistry) throws Exception {
        RequestMatcher passwordRecoveryRequest =
                SecurityConfig::isPasswordRecoveryRequest;
        RequestMatcher localPasswordRecoveryRequest = new AndRequestMatcher(
                passwordRecoveryRequest,
                SecurityConfig::isLoopbackRequest);
        HttpSessionRequestCache requestCache = new HttpSessionRequestCache();
        requestCache.setRequestMatcher(request ->
                !"/cart/count".equals(request.getRequestURI()
                        .substring(request.getContextPath().length())));

        http
            // 웹훅 및 알림 REST API 경로 CSRF 제외 — 전체 비활성화 금지
            .csrf(csrf -> csrf.ignoringRequestMatchers("/webhooks/toss", "/api/notifications/**"))
            .requestCache(cache -> cache.requestCache(requestCache))
            .authorizeHttpRequests(auth -> {
                // ① 공개 GET을 먼저 선언 (matcher 순서 = 우선순위)
                // 이메일/SMS 인증 전 간편 재설정은 local 프로필에서도 이 PC의 요청만 허용한다.
                auth.requestMatchers(localPasswordRecoveryRequest).permitAll();
                auth.requestMatchers(passwordRecoveryRequest).denyAll();
                auth.requestMatchers(
                        "/", "/login", "/signup", "/join", "/emailCheck", "/find-email",
                        "/find-email/login", "/api/notifications/unread-count", "/api/notifications/test-sms",
                        "/products/**", "/cart", "/screens", "/favicon.ico",
                        "/css/**", "/js/**", "/images/**", "/uploads/**", "/error")
                        .permitAll();
                // 로드밸런서/헬스체크가 인증 없이 호출할 수 있도록 허용 (그 외 actuator 엔드포인트는 미노출)
                auth.requestMatchers("/actuator/health", "/actuator/health/**").permitAll();
                auth.requestMatchers(HttpMethod.GET, "/community", "/community/{id:\\d+}").permitAll();
                auth.requestMatchers(HttpMethod.POST, "/webhooks/toss").permitAll();

                if (publicPreview) {
                    // local 프로필에서만 고객 목업 흐름을 로그인 없이 확인한다.
                    // 관리자 화면은 preview에서도 열지 않는다 — 아래 /admin/** 규칙에 따라 관리자 로그인이 필요하다.
                    // /community/new는 목업이 아니게 되면서 뺐다. 저장 경로가 생긴 화면을 비로그인에게
                    // 열어 두면 폼을 다 채우고 등록에서야 로그인으로 튕긴다.
                    auth.requestMatchers(
                            HttpMethod.GET,
                            "/orders/**", "/notifications",
                            "/reviews/**", "/chat")
                            .permitAll();
                }

                // ② 관리자. 모든 관리자 화면은 관리자 로그인을 요구한다.
                auth.requestMatchers("/admin/**").hasRole("ADMIN");
                // ③ 나머지는 로그인 회원
                auth.anyRequest().authenticated();
            })
            .formLogin(form -> form
                .loginPage("/login")
                // 화면과 도메인 모두 이메일을 로그인 식별자로 사용한다.
                .usernameParameter("email")
                .failureUrl("/login?error")
                // 역할별 기본 진입점 분기: 관리자 → /admin, 고객 → /mypage (저장된 요청이 있으면 그 경로 우선)
                .successHandler(new RoleBasedAuthenticationSuccessHandler())
                .permitAll()
            )
            .sessionManagement(session -> session
                .maximumSessions(-1)
                .sessionRegistry(sessionRegistry)
            )
            .logout(logout -> logout
                .logoutUrl("/logout")
                .logoutSuccessUrl("/?logout")
                .invalidateHttpSession(true)
                .clearAuthentication(true)
                .deleteCookies("JSESSIONID")
                .permitAll()
            );
        return http.build();
    }

    @Bean
    public SessionRegistry sessionRegistry() {
        return new SessionRegistryImpl();
    }

    @Bean
    public HttpSessionEventPublisher httpSessionEventPublisher() {
        return new HttpSessionEventPublisher();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    private static boolean isPasswordRecoveryRequest(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return "/find-password".equals(path)
                || "/find-password/verify".equals(path)
                || "/reset-password".equals(path);
    }

    private static boolean isLoopbackRequest(HttpServletRequest request) {
        try {
            return InetAddress.getByName(request.getRemoteAddr()).isLoopbackAddress();
        } catch (UnknownHostException exception) {
            return false;
        }
    }
}
