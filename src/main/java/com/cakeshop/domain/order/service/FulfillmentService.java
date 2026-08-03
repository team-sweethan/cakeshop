package com.cakeshop.domain.order.service;

import com.cakeshop.domain.order.dto.form.FulfillmentSearchCondition;
import com.cakeshop.domain.order.dto.view.FulfillmentListView;
import com.cakeshop.domain.order.entity.Order;
import com.cakeshop.domain.order.entity.OrderItem;
import com.cakeshop.domain.order.entity.OrderItemOption;
import com.cakeshop.domain.order.entity.OrderStatus;
import com.cakeshop.domain.order.error.OrderErrorCode;
import com.cakeshop.domain.order.mapper.OrderMapper;
import com.cakeshop.global.error.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** 픽업 가능한 주문의 수령 완료 처리를 담당한다. */
@Service
@RequiredArgsConstructor
public class FulfillmentService {

    private static final Set<OrderStatus> FULFILLMENT_STATUSES = Set.of(
            OrderStatus.UNDER_REVIEW,
            OrderStatus.READY_FOR_PICKUP,
            OrderStatus.PICKED_UP
    );

    private final OrderMapper orderMapper;
    private final Clock clock;

    /** 선택한 픽업일의 실제 제작·픽업 주문과 상품·옵션 스냅샷을 조회한다. */
    @Transactional(readOnly = true)
    public FulfillmentListView getFulfillments(FulfillmentSearchCondition condition) {
        LocalDate pickupDate = condition == null || condition.getPickupDate() == null
                ? LocalDate.now(clock)
                : condition.getPickupDate();
        OrderStatus selectedStatus = normalizeStatus(
                condition == null ? null : condition.getStatus()
        );

        List<FulfillmentListView.FulfillmentOrder> orders = orderMapper
                .findFulfillmentOrders(
                        pickupDate.atStartOfDay(),
                        pickupDate.plusDays(1).atStartOfDay(),
                        selectedStatus
                )
                .stream()
                .map(this::toFulfillmentOrder)
                .toList();

        return new FulfillmentListView(pickupDate, selectedStatus, orders);
    }

    /** DONE 결제가 유지되는 픽업 준비 주문만 수령 완료로 원자적으로 변경한다. */
    @Transactional
    public void markPickedUp(long orderId, long adminMemberId) {
        if (orderId <= 0 || adminMemberId <= 0) {
            throw new BusinessException(OrderErrorCode.INVALID_STATUS_TRANSITION);
        }
        int affectedRows = orderMapper.markPickedUpIfReady(
                orderId,
                adminMemberId,
                LocalDateTime.now(clock)
        );
        if (affectedRows != 1) {
            throw new BusinessException(OrderErrorCode.INVALID_STATUS_TRANSITION);
        }
    }

    private OrderStatus normalizeStatus(OrderStatus status) {
        return status != null && FULFILLMENT_STATUSES.contains(status)
                ? status
                : null;
    }

    private FulfillmentListView.FulfillmentOrder toFulfillmentOrder(Order order) {
        List<OrderItem> orderItems = orderMapper.findOrderItemsByOrderId(order.getId());
        Map<Long, List<OrderItemOption>> optionsByItem = orderMapper
                .findOrderItemOptionsByOrderId(order.getId())
                .stream()
                .collect(Collectors.groupingBy(OrderItemOption::getOrderItemId));

        List<FulfillmentListView.Item> items = orderItems.stream()
                .map(item -> new FulfillmentListView.Item(
                        item.getProductName(),
                        item.getQuantity(),
                        optionsByItem.getOrDefault(item.getId(), List.of())
                                .stream()
                                .map(option -> option.getOptionGroupName()
                                        + ": " + option.getOptionName())
                                .toList()
                ))
                .toList();

        return new FulfillmentListView.FulfillmentOrder(
                order.getId(),
                order.getOrderNumber(),
                order.getOrderType(),
                order.getStatus(),
                order.getPickupName(),
                order.getPickupPhone(),
                order.getPickupAt(),
                items
        );
    }
}
