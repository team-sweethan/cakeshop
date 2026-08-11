package com.cakeshop.domain.member.controller;

import com.cakeshop.domain.member.dto.form.EmailVerificationConfirmRequest;
import com.cakeshop.domain.member.dto.form.EmailVerificationRequest;
import com.cakeshop.domain.member.dto.view.EmailVerificationResponse;
import com.cakeshop.domain.member.service.EmailVerificationService;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class EmailVerificationController {

    static final String SIGNUP_VERIFIED_EMAIL_SESSION_KEY = "signupVerifiedEmail";

    private final EmailVerificationService emailVerificationService;

    // 회원가입 이메일 인증번호 발송
    @PostMapping("/email-verifications/signup/send")
    public EmailVerificationResponse sendSignupCode(
            @Valid @RequestBody EmailVerificationRequest request) {
        emailVerificationService.sendSignupCode(request.email());
        return EmailVerificationResponse.success("인증번호를 발송했습니다.");
    }

    // 회원가입 이메일 인증번호 확인
    @PostMapping("/email-verifications/signup/verify")
    public EmailVerificationResponse verifySignupCode(
            @Valid @RequestBody EmailVerificationConfirmRequest request,
            HttpSession session) {
        String verifiedEmail =
                emailVerificationService.verifySignupCode(request.email(), request.code());
        session.setAttribute(SIGNUP_VERIFIED_EMAIL_SESSION_KEY, verifiedEmail);
        return EmailVerificationResponse.success("이메일 인증이 완료되었습니다.");
    }
}
