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
        String requestMessage,
        String rejectReason,
        LocalDateTime canceledAt,
        String cancelReason,
        LocalDateTime createdAt,
        List<Item> items
) {

    public String statusLabel() {
        return switch (status) {
            case PENDING_PAYMENT -> "결제 대기";
            case UNDER_REVIEW -> "승인 대기";
            case READY_FOR_PICKUP -> "픽업 준비";
            case PICKED_UP -> "픽업 완료";
            case CANCELED -> "취소 완료";
            case REJECTED -> "주문 반려";
            case EXPIRED -> "결제 만료";
        };
    }

    public boolean paymentPending() {
        return status == OrderStatus.PENDING_PAYMENT;
    }

    public boolean readyForPickup() {
        return status == OrderStatus.READY_FOR_PICKUP;
    }

    public boolean cancelRequestAvailable() {
        return orderType == OrderType.GENERAL && status == OrderStatus.READY_FOR_PICKUP;
    }

    public boolean adminCancellationAvailable() {
        return cancelRequestAvailable();
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
