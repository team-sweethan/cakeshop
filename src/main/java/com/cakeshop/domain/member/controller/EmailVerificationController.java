package com.cakeshop.domain.member.controller;

import com.cakeshop.domain.member.dto.form.EmailVerificationConfirmRequest;
import com.cakeshop.domain.member.dto.form.EmailVerificationRequest;
import com.cakeshop.domain.member.dto.view.EmailVerificationResponse;
import com.cakeshop.domain.member.dto.view.SignupEmailVerification;
import com.cakeshop.domain.member.dto.view.PasswordResetEmailVerification;
import com.cakeshop.domain.member.service.EmailVerificationService;
import com.cakeshop.domain.member.service.PasswordResetEmailDispatchService;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
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
    private final PasswordResetEmailDispatchService passwordResetEmailDispatchService;

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
        SignupEmailVerification verification =
                emailVerificationService.verifySignupCode(request.email(), request.code());
        session.setAttribute(SIGNUP_VERIFIED_EMAIL_SESSION_KEY, verification);
        return EmailVerificationResponse.success("이메일 인증이 완료되었습니다.");
    }

    // 비밀번호 재설정 이메일 인증번호 발송
    @PostMapping("/email-verifications/password-reset/send")
    public EmailVerificationResponse sendPasswordResetCode(
            @Valid @RequestBody EmailVerificationRequest request) {
        passwordResetEmailDispatchService.dispatch(request.email());
        return EmailVerificationResponse.success(
                "입력한 이메일로 인증번호 발송을 요청했습니다.");
    }

    // 비밀번호 재설정 이메일 인증번호 확인
    @PostMapping("/email-verifications/password-reset/verify")
    public EmailVerificationResponse verifyPasswordResetCode(
            @Valid @RequestBody EmailVerificationConfirmRequest request,
            HttpServletRequest httpRequest) {
        PasswordResetEmailVerification verification =
                emailVerificationService.verifyPasswordResetCode(
                        request.email(), request.code());
        HttpSession session = httpRequest.getSession();
        httpRequest.changeSessionId();
        session.setAttribute(
                PasswordRecoveryController.PASSWORD_RECOVERY_SESSION_KEY,
                new PasswordRecoverySession(
                        verification.verificationId(),
                        verification.memberId(),
                        verification.email(),
                        verification.expiresAt()));
        return EmailVerificationResponse.success("이메일 인증이 완료되었습니다.");
    }
}
