package com.cakeshop.domain.order.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 결제 기한이 지난 주문과 결제를 정기적으로 만료 처리한다. */
@Component
public class OrderExpireScheduler {

    /** 결제 기한이 지난 PENDING_PAYMENT 주문을 EXPIRED로 전이한다. */
    @Scheduled(fixedDelayString = "60000")
    public void expireOverdueOrders() {
        // TODO: 결제 완료와의 경합을 막도록 조건부 상태 변경으로 구현
    }
}
