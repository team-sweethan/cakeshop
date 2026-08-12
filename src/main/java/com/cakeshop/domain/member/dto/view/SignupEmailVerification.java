package com.cakeshop.domain.member.dto.view;

import java.io.Serializable;

/** 회원가입 세션에 보관하는 이메일 인증 결과다. */
public record SignupEmailVerification(Long verificationId, String email)
        implements Serializable {
}
