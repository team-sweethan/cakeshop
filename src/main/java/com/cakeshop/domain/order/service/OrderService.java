package com.cakeshop.domain.order.service;

import com.cakeshop.domain.order.dto.form.GeneralOrderForm;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** 주문 도메인이 다른 계층과 도메인에 공개하는 업무 계약이다. */
public interface OrderService {

    /**
     * 일반 상품 주문과 결제 대기 정보를 생성한다.
     *
     * @return 결제 화면으로 이동할 때 사용할 주문 ID
     */
    long createGeneralOrder(long memberId, GeneralOrderForm form);

    /** 결제 검증과 재고 차감에 필요한 회원 소유의 일반 주문을 조회한다. */
    GeneralPaymentOrder getGeneralPaymentOrder(long memberId, long orderId);

    /** 결제가 완료된 일반 주문을 픽업 대기 상태로 변경한다. */
    void completeGeneralOrderAfterPayment(
            long orderId,
            LocalDateTime readyAt
    );

    /** 결제 검증과 완료 처리에 필요한 일반 주문 정보다. */
    record GeneralPaymentOrder(
            long orderId,
            BigDecimal amount,
            LocalDateTime paymentExpiresAt,
            List<PaymentProduct> products
    ) {
    }

    /** 결제 성공 시 재고를 차감할 주문 상품이다. */
    record PaymentProduct(long productId, int quantity) {
    }
}
