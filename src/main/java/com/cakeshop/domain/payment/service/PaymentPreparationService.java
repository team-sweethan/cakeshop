package com.cakeshop.domain.payment.service;

import java.math.BigDecimal;

/** 주문 생성 시 결제 대기 정보를 준비하는 결제 도메인의 공개 계약이다. */
public interface PaymentPreparationService {

    /** 주문에 연결된 READY 결제를 생성한다. */
    void prepareReadyPayment(
            long orderId,
            String orderNumber,
            BigDecimal amount
    );
}
