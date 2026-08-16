package com.cakeshop.domain.order.dto.view.customer;

import com.cakeshop.domain.order.dto.view.customer.common.CheckoutOptionView;
import com.cakeshop.domain.order.dto.view.customer.common.PickupDateView;
import java.math.BigDecimal;
import java.util.List;

/** 장바구니 다건 주문서에 표시할 서버 재검증 기준 상품·금액 정보다. */
public record CartOrderCheckoutView(
        List<CartOrderItemView> items,
        BigDecimal productAmount,
        BigDecimal optionAmount,
        BigDecimal totalAmount,
        List<PickupDateView> pickupDates
) {
    public record CartOrderItemView(
            String productName,
            int quantity,
            List<CheckoutOptionView> selectedOptions,
            BigDecimal totalAmount
    ) {
    }
}
