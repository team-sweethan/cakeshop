package com.cakeshop.domain.order.dto.view.customer;

import com.cakeshop.domain.order.dto.view.customer.common.CheckoutOptionView;
import com.cakeshop.domain.order.dto.view.customer.common.PickupDateView;
import java.math.BigDecimal;
import java.util.List;

/** 수제 주문 요청 화면이 서버 기준으로 표시할 상품·옵션·픽업·금액 정보다. */
public record CustomOrderCheckoutView(
        long productId,
        String productName,
        int preparationDays,
        List<CheckoutOptionView> selectedOptions,
        BigDecimal totalAmount,
        List<PickupDateView> pickupDates
) {
}
