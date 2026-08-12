package com.cakeshop.domain.member.service;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.cakeshop.domain.member.error.MemberErrorCode;
import com.cakeshop.global.error.BusinessException;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.SyncTaskExecutor;

class PasswordResetEmailDispatchServiceTests {

    private EmailVerificationService emailVerificationService;
    private PasswordResetEmailDispatchService dispatchService;

    @BeforeEach
    void setUp() {
        emailVerificationService = org.mockito.Mockito.mock(EmailVerificationService.class);
        dispatchService = new PasswordResetEmailDispatchService(
                emailVerificationService,
                new SyncTaskExecutor());
    }

    @Test
    void dispatch_returnsBeforePasswordResetSendTaskRuns() {
        AtomicReference<Runnable> pendingTask = new AtomicReference<>();
        dispatchService = new PasswordResetEmailDispatchService(
                emailVerificationService,
                pendingTask::set);

        dispatchService.dispatch("member@example.com");

        verifyNoInteractions(emailVerificationService);

        pendingTask.get().run();

        verify(emailVerificationService).sendPasswordResetCode("member@example.com");
    }

    @Test
    void dispatch_hiddenFailure_doesNotPropagate() {
        doThrow(new BusinessException(MemberErrorCode.EMAIL_VERIFICATION_RATE_LIMITED))
                .when(emailVerificationService)
                .sendPasswordResetCode("missing@example.com");

        dispatchService.dispatch("missing@example.com");

        verify(emailVerificationService).sendPasswordResetCode("missing@example.com");
    }
}
