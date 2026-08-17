package com.cakeshop.domain.order.dto.view.customer;

import com.cakeshop.domain.order.dto.view.customer.common.CheckoutOptionView;
import com.cakeshop.domain.order.dto.view.customer.common.PickupDateView;

import java.math.BigDecimal;
import java.util.List;

/** 일반 상품 주문서에 표시할 상품, 금액, 픽업 가능 일시를 조합한 조회 결과다. */
public record GeneralOrderCheckoutView(
        long productId,
        String productName,
        String productTypeLabel,
        int quantity,
        List<CheckoutOptionView> selectedOptions,
        BigDecimal productAmount,
        BigDecimal optionAmount,
        BigDecimal totalAmount,
        List<PickupDateView> pickupDates
) {
}
