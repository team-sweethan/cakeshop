package com.cakeshop.global.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.cakeshop.domain.member.dto.view.MemberAuthenticationView;
import com.cakeshop.domain.member.service.MemberAuthenticationService;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

@ExtendWith(MockitoExtension.class)
class MemberDetailsServiceTests {

    private static final String LOCAL_ADMIN_HASH =
        "$2a$10$wRIE78x8sm..uLtbp9LHde7l6wUWQD3NjPvThQaXvZ3PpXfW6wwX.";

    @Mock
    private MemberAuthenticationService memberAuthenticationService;

    private MemberDetailsService memberDetailsService;

    @BeforeEach
    void setUp() {
        memberDetailsService = new MemberDetailsService(memberAuthenticationService);
    }

    @Test
    void loadUserByUsername_adminEmail_loadsAdminAuthority() {
        MemberAuthenticationView admin = new MemberAuthenticationView(
                1L,
                "admin@cakeshop.local",
                LOCAL_ADMIN_HASH,
                "ADMIN",
                true);
        when(memberAuthenticationService.findForAuthentication(admin.email()))
                .thenReturn(Optional.of(admin));

        MemberDetails details = (MemberDetails) memberDetailsService.loadUserByUsername(admin.email());

        assertThat(details.getMemberId()).isEqualTo(1L);
        assertThat(details.getAuthorities()).extracting("authority").containsExactly("ROLE_ADMIN");
    }

    @Test
    void passwordMatches_localAdminHash_matchesDocumentedPassword() {
        assertThat(new BCryptPasswordEncoder().matches("Admin1234!", LOCAL_ADMIN_HASH)).isTrue();
    }

    @Test
    void loadUserByUsername_unknownEmail_failsAuthentication() {
        when(memberAuthenticationService.findForAuthentication("missing@cakeshop.local"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> memberDetailsService.loadUserByUsername("missing@cakeshop.local"))
            .isInstanceOf(UsernameNotFoundException.class);
    }

    @Test
    void loadUserByUsername_withdrawnMember_failsAuthentication() {
        MemberAuthenticationView withdrawn = new MemberAuthenticationView(
                1L,
                "withdrawn@cakeshop.local",
                "encoded-password",
                "USER",
                false);
        when(memberAuthenticationService.findForAuthentication(withdrawn.email()))
                .thenReturn(Optional.of(withdrawn));

        assertThatThrownBy(() -> memberDetailsService.loadUserByUsername(withdrawn.email()))
                .isInstanceOf(UsernameNotFoundException.class)
                .hasMessage("이메일 또는 비밀번호가 올바르지 않습니다.");
    }
}
