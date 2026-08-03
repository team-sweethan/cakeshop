package com.cakeshop.domain.payment.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 서버 재시작이나 일시 장애 뒤 남은 결제 보상 취소를 주기적으로 재처리한다. */
@Component
public class PaymentRecoveryScheduler {

    private final PaymentFacade paymentFacade;
    private final int batchSize;

    public PaymentRecoveryScheduler(
            PaymentFacade paymentFacade,
            @Value("${app.payment.recovery.batch-size:50}") int batchSize
    ) {
        this.paymentFacade = paymentFacade;
        this.batchSize = batchSize;
    }

    @Scheduled(
            fixedDelayString = "${app.payment.recovery.fixed-delay:60s}",
            initialDelayString = "${app.payment.recovery.initial-delay:60s}"
    )
    public void recoverPendingCompensations() {
        paymentFacade.recoverPendingCompensations(batchSize);
    }
}
