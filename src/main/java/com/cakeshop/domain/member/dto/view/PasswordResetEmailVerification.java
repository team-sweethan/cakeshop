package com.cakeshop.domain.member.dto.view;

import java.io.Serializable;

/** 비밀번호 재설정 세션에 보관하는 이메일 인증 결과다. */
public record PasswordResetEmailVerification(
        Long verificationId,
        Long memberId,
        String email) implements Serializable {
}
