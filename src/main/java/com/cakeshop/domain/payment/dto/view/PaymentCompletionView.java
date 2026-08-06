package com.cakeshop.domain.payment.dto.view;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** 결제 승인 후 주문 완료 화면에 표시할 실제 주문·결제 정보다. */
public record PaymentCompletionView(
        long orderId,
        String orderNumber,
        BigDecimal amount,
        String method,
        LocalDateTime pickupAt,
        String pickupPlace,
        List<ItemView> items
) {

    public record ItemView(String productName, int quantity) {
    }
}
