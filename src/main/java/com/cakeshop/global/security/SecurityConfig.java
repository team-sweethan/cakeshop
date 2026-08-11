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
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.DefaultRedirectStrategy;
import org.springframework.security.web.RedirectStrategy;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.access.AccessDeniedHandlerImpl;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
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
        LoginUrlAuthenticationEntryPoint customerLoginEntryPoint =
                new LoginUrlAuthenticationEntryPoint("/login");
        LoginUrlAuthenticationEntryPoint adminLoginEntryPoint =
                new LoginUrlAuthenticationEntryPoint("/admin/login");
        AuthenticationEntryPoint portalLoginEntryPoint = (request, response, exception) -> {
            String path = request.getRequestURI()
                    .substring(request.getContextPath().length());
            if ("/admin".equals(path) || path.startsWith("/admin/")) {
                adminLoginEntryPoint.commence(request, response, exception);
                return;
            }
            customerLoginEntryPoint.commence(request, response, exception);
        };
        AccessDeniedHandler defaultAccessDeniedHandler = new AccessDeniedHandlerImpl();
        RedirectStrategy redirectStrategy = new DefaultRedirectStrategy();
        AccessDeniedHandler portalAccessDeniedHandler = (request, response, exception) -> {
            boolean admin = request.getUserPrincipal() instanceof Authentication auth
                    && auth.getAuthorities().stream()
                    .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
            if (admin && HttpMethod.GET.matches(request.getMethod())) {
                redirectStrategy.sendRedirect(request, response, "/admin");
                return;
            }
            defaultAccessDeniedHandler.handle(request, response, exception);
        };

        http
            // 웹훅 경로만 CSRF 제외 — 전체 비활성화 금지
                .csrf(csrf -> csrf.ignoringRequestMatchers("/webhooks/toss"))
            .requestCache(cache -> cache.requestCache(requestCache))
            .authorizeHttpRequests(auth -> {
                // ① 공개 GET을 먼저 선언 (matcher 순서 = 우선순위)
                // 이메일/SMS 인증 전 간편 재설정은 local 프로필에서도 이 PC의 요청만 허용한다.
                auth.requestMatchers(localPasswordRecoveryRequest).permitAll();
                auth.requestMatchers(passwordRecoveryRequest).denyAll();
                auth.requestMatchers(
                        "/", "/login", "/signup", "/join", "/emailCheck", "/find-email",
                        "/find-email/login", "/api/notifications/unread-count", "/api/notifications/test-sms",
                        "/products/**", "/screens", "/favicon.ico",
                        "/css/**", "/js/**", "/webjars/**", "/images/**", "/uploads/**", "/error")
                        .permitAll();
                auth.requestMatchers("/admin/login").permitAll();
                // 로드밸런서/헬스체크가 인증 없이 호출할 수 있도록 허용 (그 외 actuator 엔드포인트는 미노출)
                auth.requestMatchers("/actuator/health", "/actuator/health/**").permitAll();
                auth.requestMatchers(HttpMethod.GET, "/community", "/community/{id:\\d+}").permitAll();
                auth.requestMatchers(HttpMethod.POST, "/webhooks/toss").permitAll();

                if (publicPreview) {
                    // local 프로필에서만 고객 목업 흐름을 로그인 없이 확인한다.
                    // 관리자 화면은 preview에서도 열지 않는다 — 아래 /admin/** 규칙에 따라 관리자 로그인이 필요하다.
                    // /community/new는 목업이 아니게 되면서 뺐다. 저장 경로가 생긴 화면을 비로그인에게
                    // 열어 두면 폼을 다 채우고 등록에서야 로그인으로 튕긴다.
                    // /reviews/**도 조각 1에서 같은 이유로 뺐다 — 작성 폼이 주문 소유권을 검증하므로
                    // 익명 사용자는 폼을 다 채운 뒤에야 튕긴다.
                    auth.requestMatchers(
                            HttpMethod.GET,
                            "/orders/**", "/notifications", "/api/notifications", "/api/notifications/**",
                            "/chat")
                            .permitAll();
                }

                // ② 관리자. 모든 관리자 화면은 관리자 로그인을 요구한다.
                auth.requestMatchers("/admin", "/admin/**").hasRole("ADMIN");
                // 고객과 관리자가 각자 받은 알림을 같은 API에서 조회하고 읽음 처리한다.
                auth.requestMatchers("/api/notifications", "/api/notifications/**")
                        .hasAnyRole("USER", "ADMIN");
                // ③ 나머지 회원 전용 기능은 일반 회원만 사용한다.
                auth.anyRequest().hasRole("USER");
            })
            .exceptionHandling(exception -> exception
                .authenticationEntryPoint(portalLoginEntryPoint)
                .accessDeniedHandler(portalAccessDeniedHandler))
            .formLogin(form -> form
                .loginPage("/login")
                // 화면과 도메인 모두 이메일을 로그인 식별자로 사용한다.
                .usernameParameter("email")
                .failureHandler(new PortalAuthenticationFailureHandler())
                // 역할별 기본 진입점 분기: 관리자 → /admin, 고객 → / (저장된 요청이 있으면 그 경로 우선)
                .successHandler(new RoleBasedAuthenticationSuccessHandler())
                .permitAll()
            )
            .sessionManagement(session -> session
                .maximumSessions(-1)
                .sessionRegistry(sessionRegistry)
            )
            .logout(logout -> logout
                .logoutUrl("/logout")
                .logoutSuccessHandler(new PortalLogoutSuccessHandler())
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
