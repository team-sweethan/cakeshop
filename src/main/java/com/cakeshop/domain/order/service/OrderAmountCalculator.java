package com.cakeshop.domain.order.service;

import com.cakeshop.domain.order.error.OrderErrorCode;
import com.cakeshop.domain.order.service.OrderOptionValidator.ValidatedOption;
import com.cakeshop.global.error.BusinessException;
import java.math.BigDecimal;
import java.util.List;

/** 주문서 조회와 주문 확정이 공유하는 일반 상품 금액 계산 규칙이다. */
public final class OrderAmountCalculator {

    private static final BigDecimal MAX_ORDER_AMOUNT = new BigDecimal("999999999999");

    private OrderAmountCalculator() {
    }

    public static OrderAmounts calculate(
            BigDecimal basePrice,
            int quantity,
            List<ValidatedOption> selectedOptions
    ) {
        BigDecimal unitOptionAmount = selectedOptions.stream()
                .map(ValidatedOption::additionalPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal productAmount = basePrice.multiply(BigDecimal.valueOf(quantity));
        BigDecimal optionAmount = unitOptionAmount.multiply(BigDecimal.valueOf(quantity));
        BigDecimal totalAmount = productAmount.add(optionAmount);

        if (totalAmount.compareTo(MAX_ORDER_AMOUNT) > 0) {
            throw new BusinessException(OrderErrorCode.ORDER_AMOUNT_EXCEEDED);
        }

        return new OrderAmounts(productAmount, optionAmount, totalAmount, unitOptionAmount);
    }

    public record OrderAmounts(
            BigDecimal productAmount,
            BigDecimal optionAmount,
            BigDecimal totalAmount,
            BigDecimal unitOptionAmount
    ) {
    }
}
