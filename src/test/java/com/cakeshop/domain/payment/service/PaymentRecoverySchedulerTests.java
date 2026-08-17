package com.cakeshop.domain.payment.service;

import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class PaymentRecoverySchedulerTests {

    @Test
    void recoverPendingCompensations_configuredBatchSize_delegatesToRecoveryProcessors() {
        PaymentCompensationProcessor compensationProcessor = Mockito.mock(PaymentCompensationProcessor.class);
        RefundFacade refundFacade = Mockito.mock(RefundFacade.class);
        PaymentRecoveryScheduler scheduler = new PaymentRecoveryScheduler(
                compensationProcessor,
                refundFacade,
                25
        );

        scheduler.recoverPendingCompensations();

        verify(compensationProcessor).recoverPendingCompensations(25);
        verify(refundFacade).recoverPendingCancellations(25);
    }
}
