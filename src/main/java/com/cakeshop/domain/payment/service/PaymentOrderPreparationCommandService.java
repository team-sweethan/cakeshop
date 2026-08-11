package com.cakeshop.domain.payment.service;

import java.math.BigDecimal;

/**
 * ******************************
 * 작성자 : 주환
 * 담당자 : 주환
 * 작성일 : 2026-08-11
 * 기능 : 주문 생성용 READY 결제 준비 명령 계약
 * 설명 : 주문 도메인이 payments 테이블을 직접 변경하지 않고 주문에 연결된 READY 결제 생성을 요청하도록 제공한다.
 * ******************************
 */
public interface PaymentOrderPreparationCommandService {

    /**
     * ******************************
     * 작성자 : 주환
     * 담당자 : 주환
     * 작성일 : 2026-08-11
     * 기능 : READY 결제 생성
     * 설명 : 일반 주문 생성 트랜잭션 안에서 주문 번호와 서버 금액으로 결제 대기 정보를 생성한다.
     * ******************************
     */
    void prepareReadyPayment(
            long orderId,
            String orderNumber,
            BigDecimal amount
    );
}
