package com.cakeshop.domain.member.dto.view;

public record RecoveredEmailView(
        String email,
        String maskedEmail) {
}
