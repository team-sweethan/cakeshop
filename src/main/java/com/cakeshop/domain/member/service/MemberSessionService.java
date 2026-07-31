package com.cakeshop.domain.member.service;

import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

@Service
public class MemberSessionService {

    private final SessionRegistry sessionRegistry;

    public MemberSessionService(SessionRegistry sessionRegistry) {
        this.sessionRegistry = sessionRegistry;
    }

    public void expireSessionsByEmail(String email) {
        sessionRegistry.getAllPrincipals().stream()
                .filter(UserDetails.class::isInstance)
                .map(UserDetails.class::cast)
                .filter(principal ->
                        email.equals(principal.getUsername()))
                .flatMap(principal ->
                        sessionRegistry
                                .getAllSessions(principal, false)
                                .stream())
                .forEach(SessionInformation::expireNow);
    }
}
