package com.cakeshop.domain.member.dto.view;

import java.util.List;

public record EmailRecoveryResult(
        List<String> emails,
        List<RecoveredEmailView> views) {

    public EmailRecoveryResult {
        emails = List.copyOf(emails);
        views = List.copyOf(views);
    }
}
