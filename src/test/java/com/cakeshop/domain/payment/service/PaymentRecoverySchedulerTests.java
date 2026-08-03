package com.cakeshop.domain.payment.service;

import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class PaymentRecoverySchedulerTests {

    @Test
    void recoverPendingCompensations_configuredBatchSize_delegatesToFacade() {
        PaymentFacade paymentFacade = Mockito.mock(PaymentFacade.class);
        PaymentRecoveryScheduler scheduler = new PaymentRecoveryScheduler(
                paymentFacade,
                25
        );

        scheduler.recoverPendingCompensations();

        verify(paymentFacade).recoverPendingCompensations(25);
    }
}
