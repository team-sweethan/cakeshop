package com.cakeshop.domain.member.service;

import com.cakeshop.global.error.BusinessException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;

@Service
public class PasswordResetEmailDispatchService {

    private final EmailVerificationService emailVerificationService;
    private final TaskExecutor taskExecutor;

    public PasswordResetEmailDispatchService(
            EmailVerificationService emailVerificationService,
            @Qualifier("applicationTaskExecutor") TaskExecutor taskExecutor) {
        this.emailVerificationService = emailVerificationService;
        this.taskExecutor = taskExecutor;
    }

    /** 회원 조회부터 SMTP 발송까지 백그라운드에서 수행한다. */
    public void dispatch(String email) {
        taskExecutor.execute(() -> {
            try {
                emailVerificationService.sendPasswordResetCode(email);
            } catch (BusinessException ignored) {
                // 회원 존재 여부와 SMTP 상태를 공개 응답으로 노출하지 않는다.
            }
        });
    }
}
