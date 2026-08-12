package com.cakeshop.domain.member.service;

import java.time.Duration;

/** 회원 이메일 인증 메시지를 전달하는 발송 계약이다. */
public interface EmailSender {

    void sendVerificationCode(String recipient, String code, Duration validFor);
}
