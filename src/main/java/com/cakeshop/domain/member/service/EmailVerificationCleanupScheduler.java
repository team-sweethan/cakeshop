package com.cakeshop.domain.member.service;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class EmailVerificationCleanupScheduler {

    private final EmailVerificationService emailVerificationService;

    /** 매일 새벽 4시에 보관 기간이 지난 인증 요청을 정리한다. */
    @Scheduled(cron = "0 0 4 * * *", zone = "Asia/Seoul")
    public void cleanup() {
        emailVerificationService.deleteExpiredVerifications();
    }
}
