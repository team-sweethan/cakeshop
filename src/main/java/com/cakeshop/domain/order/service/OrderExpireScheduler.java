package com.cakeshop.domain.order.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import lombok.RequiredArgsConstructor;

/** 결제 기한이 지난 주문과 결제를 정기적으로 만료 처리한다. */
@Component
@RequiredArgsConstructor
public class OrderExpireScheduler {

    private final OrderExpirationService orderExpirationService;

    /** 결제 기한이 지난 PENDING_PAYMENT 주문을 EXPIRED로 전이한다. */
    @Scheduled(
            initialDelayString = "${app.order.expiration.initial-delay-ms:60000}",
            fixedDelayString = "${app.order.expiration.fixed-delay-ms:60000}"
    )
    public void expireOverdueOrders() {
        orderExpirationService.expireOverdueOrders();
    }
}
