package com.cakeshop.domain.order.service;

import com.cakeshop.domain.order.dto.form.customer.GeneralOrderForm;


/** 일반 상품 주문 생성과 결제 연동을 제공하는 주문 도메인의 공개 계약이다. */
public interface OrderService {

    /**
     * 일반 상품 주문과 결제 대기 정보를 생성한다.
     *
     * @return 결제 화면으로 이동할 때 사용할 주문 ID
     */
    long createGeneralOrder(long memberId, GeneralOrderForm form);

}
