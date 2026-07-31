package com.cakeshop.domain.member.service;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.userdetails.UserDetails;

class MemberSessionServiceTests {

    @Test
    void expireSessionsByEmail_matchingUser_expiresAllSessions() {
        SessionRegistry sessionRegistry = mock(SessionRegistry.class);
        UserDetails targetPrincipal = mock(UserDetails.class);
        UserDetails otherPrincipal = mock(UserDetails.class);
        SessionInformation firstSession = mock(SessionInformation.class);
        SessionInformation secondSession = mock(SessionInformation.class);

        when(targetPrincipal.getUsername())
                .thenReturn("target@example.com");
        when(otherPrincipal.getUsername())
                .thenReturn("other@example.com");
        when(sessionRegistry.getAllPrincipals())
                .thenReturn(List.of(
                        targetPrincipal,
                        otherPrincipal,
                        "non-user-details"));
        when(sessionRegistry.getAllSessions(targetPrincipal, false))
                .thenReturn(List.of(firstSession, secondSession));

        MemberSessionService memberSessionService =
                new MemberSessionService(sessionRegistry);

        memberSessionService.expireSessionsByEmail(
                "target@example.com");

        verify(firstSession).expireNow();
        verify(secondSession).expireNow();
        verify(sessionRegistry, never())
                .getAllSessions(otherPrincipal, false);
    }
}
