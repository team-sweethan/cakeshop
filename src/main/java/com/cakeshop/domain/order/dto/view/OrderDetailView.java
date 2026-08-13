package com.cakeshop.domain.order.dto.view;

import com.cakeshop.domain.order.entity.OrderStatus;
import com.cakeshop.domain.order.entity.OrderType;
import com.cakeshop.domain.product.entity.ProductType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** 주문 당시 저장한 정보만으로 구성한 주문 상세 화면 모델이다. */
public record OrderDetailView(
        long orderId,
        String orderNumber,
        long memberId,
        OrderType orderType,
        OrderStatus status,
        String ordererName,
        String ordererPhone,
        String pickupName,
        String pickupPhone,
        BigDecimal originalAmount,
        BigDecimal discountAmount,
        BigDecimal finalAmount,
        LocalDateTime pickupAt,
        LocalDateTime paymentExpiresAt,
        boolean paymentPending,
        String requestMessage,
        String rejectReason,
        LocalDateTime canceledAt,
        String cancelReason,
        LocalDateTime createdAt,
        boolean cancelRequestAvailable,
        List<Item> items
) {

    public String statusLabel() {
        return status.statusLabel();
    }

    public boolean readyForPickup() {
        return status == OrderStatus.READY_FOR_PICKUP;
    }

    public boolean fulfillmentManageable() {
        return status == OrderStatus.READY_FOR_PICKUP
                || (orderType == OrderType.CUSTOM
                && (status == OrderStatus.UNDER_REVIEW || status == OrderStatus.IN_PRODUCTION));
    }

    public boolean adminCancellationAvailable() {
        return orderType == OrderType.GENERAL && cancelRequestAvailable();
    }

    public String customerCancellationGuide() {
        return orderType == OrderType.CUSTOM
                ? "주문 제작은 관리자 승인 전까지 전액 취소할 수 있습니다."
                : "일반 상품은 픽업 예정 시각 전까지 전액 취소할 수 있습니다.";
    }

    public record Item(
            long orderItemId,
            Long productId,
            String productName,
            ProductType productType,
            int quantity,
            BigDecimal basePrice,
            BigDecimal optionAmount,
            BigDecimal totalAmount,
            String requirements,
            List<Option> options,
            List<Image> images
    ) {
    }

    public record Option(
            String groupName,
            String optionName,
            BigDecimal additionalPrice
    ) {
    }

    public record Image(String imageUrl, int sortOrder) {
    }
}
