package com.cakeshop.domain.order.dto.view.admin;

import com.cakeshop.domain.order.entity.OrderStatus;
import com.cakeshop.domain.order.entity.OrderType;
import com.cakeshop.global.common.paging.PageResult;

import java.time.LocalDateTime;
import java.util.List;

/** 관리자 제작·픽업 목록과 적용된 검색 조건이다. */
public record FulfillmentListView(
        OrderStatus selectedStatus,
        PageResult<FulfillmentOrder> orders
) {

    public record FulfillmentOrder(
            long orderId,
            String orderNumber,
            OrderType orderType,
            OrderStatus status,
            String pickupName,
            String pickupPhone,
            LocalDateTime pickupAt,
            String requestMessage,
            boolean productionStartable,
            boolean productionCompletable,
            boolean rejectionAvailable,
            boolean pickupCompletable,
            List<Item> items
    ) {

        public String orderTypeLabel() {
            return orderType.orderTypeLabel();
        }

        public String statusLabel() {
            return status.statusLabel();
        }

        public String statusClass() {
            return switch (status) {
                case UNDER_REVIEW -> "badge--warning";
                case IN_PRODUCTION -> "badge--info";
                case READY_FOR_PICKUP -> "badge--success";
                case PICKED_UP -> "badge--info";
                default -> "";
            };
        }

    }

    public record Item(
            String productName,
            int quantity,
            String requirements,
            List<String> options
    ) {

        public String optionSummary() {
            return options.isEmpty() ? "기본" : String.join(" · ", options);
        }
    }
}
