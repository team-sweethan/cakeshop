package com.cakeshop.domain.order.dto.view.admin;

import com.cakeshop.domain.order.entity.OrderStatus;
import com.cakeshop.domain.order.entity.OrderType;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 관리자 제작·픽업 목록과 적용된 검색 조건이다. */
public record FulfillmentListView(
        LocalDate pickupDate,
        OrderStatus selectedStatus,
        List<FulfillmentOrder> orders
) {

    public record FulfillmentOrder(
            long orderId,
            String orderNumber,
            OrderType orderType,
            OrderStatus status,
            String pickupName,
            String pickupPhone,
            LocalDateTime pickupAt,
            List<Item> items
    ) {

        public String orderTypeLabel() {
            return orderType == OrderType.CUSTOM ? "주문 제작" : "일반 상품";
        }

        public String statusLabel() {
            return switch (status) {
                case UNDER_REVIEW -> "승인 대기";
                case READY_FOR_PICKUP -> "픽업 준비";
                case PICKED_UP -> "픽업 완료";
                default -> status.name();
            };
        }

        public String statusClass() {
            return switch (status) {
                case UNDER_REVIEW -> "badge--warning";
                case READY_FOR_PICKUP -> "badge--success";
                case PICKED_UP -> "badge--info";
                default -> "";
            };
        }

        public boolean readyForPickup() {
            return status == OrderStatus.READY_FOR_PICKUP;
        }
    }

    public record Item(
            String productName,
            int quantity,
            List<String> options
    ) {

        public String optionSummary() {
            return options.isEmpty() ? "기본" : String.join(" · ", options);
        }
    }
}
