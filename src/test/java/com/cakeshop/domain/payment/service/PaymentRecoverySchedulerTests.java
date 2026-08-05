package com.cakeshop.domain.payment.service;

import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class PaymentRecoverySchedulerTests {

    @Test
    void recoverPendingCompensations_configuredBatchSize_delegatesToFacades() {
        PaymentFacade paymentFacade = Mockito.mock(PaymentFacade.class);
        RefundFacade refundFacade = Mockito.mock(RefundFacade.class);
        PaymentRecoveryScheduler scheduler = new PaymentRecoveryScheduler(
                paymentFacade,
                refundFacade,
                25
        );

        scheduler.recoverPendingCompensations();

        verify(paymentFacade).recoverPendingCompensations(25);
        verify(refundFacade).recoverPendingCancellations(25);
    }
}
