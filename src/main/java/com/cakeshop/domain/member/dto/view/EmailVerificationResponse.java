package com.cakeshop.domain.member.dto.view;

public record EmailVerificationResponse(
        boolean success,
        String code,
        String message) {

    public static EmailVerificationResponse success(String message) {
        return new EmailVerificationResponse(true, null, message);
    }

    public static EmailVerificationResponse failure(String code, String message) {
        return new EmailVerificationResponse(false, code, message);
    }
}
