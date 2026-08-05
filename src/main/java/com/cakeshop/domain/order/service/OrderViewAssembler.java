package com.cakeshop.domain.order.service;

import com.cakeshop.domain.order.dto.view.OrderDetailView;
import com.cakeshop.domain.order.dto.view.OrderListView;
import com.cakeshop.domain.order.entity.Order;
import com.cakeshop.domain.order.entity.OrderItem;
import com.cakeshop.domain.order.entity.OrderItemImage;
import com.cakeshop.domain.order.entity.OrderItemOption;
import com.cakeshop.domain.order.entity.OrderStatus;
import com.cakeshop.domain.order.entity.OrderType;
import com.cakeshop.domain.order.mapper.OrderMapper;
import com.cakeshop.global.error.BusinessException;
import com.cakeshop.global.error.CommonErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** 고객·관리자 주문 조회가 공통으로 사용하는 주문 화면 데이터 조립을 담당한다. */
@Component
@RequiredArgsConstructor
public class OrderViewAssembler {

    private final OrderMapper orderMapper;
    private final Clock clock;

    public List<Order> findOrdersByMemberId(long memberId) {
        return orderMapper.findOrdersByMemberId(memberId);
    }

    public List<Order> findAllOrders() {
        return orderMapper.findAllOrders();
    }

    public Order findOrder(long orderId) {
        if (orderId <= 0) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND);
        }
        return orderMapper.findOrderById(orderId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
    }

    public OrderListView toListView(Order order) {
        List<OrderItem> items = orderMapper.findOrderItemsByOrderId(order.getId());
        String productName = items.isEmpty() ? "주문 상품 없음" : items.getFirst().getProductName();
        return new OrderListView(
                order.getId(), order.getOrderNumber(), order.getMemberId(), order.getOrdererName(),
                order.getOrderType(), order.getStatus(), productName, items.size(), order.getFinalAmount(),
                order.getPickupAt(), order.getCreatedAt()
        );
    }

    public OrderDetailView toDetailView(Order order) {
        List<OrderItem> items = orderMapper.findOrderItemsByOrderId(order.getId());
        Map<Long, List<OrderItemOption>> optionsByItem = orderMapper.findOrderItemOptionsByOrderId(order.getId())
                .stream().collect(Collectors.groupingBy(OrderItemOption::getOrderItemId));
        Map<Long, List<OrderItemImage>> imagesByItem = orderMapper.findOrderItemImagesByOrderId(order.getId())
                .stream().collect(Collectors.groupingBy(OrderItemImage::getOrderItemId));
        List<OrderDetailView.Item> itemViews = items.stream()
                .map(item -> toItemView(item, optionsByItem, imagesByItem))
                .toList();
        LocalDateTime now = LocalDateTime.now(clock);
        boolean cancellationRetryAvailable = orderMapper.hasRequestedRefundCancellation(order.getId());
        return new OrderDetailView(
                order.getId(), order.getOrderNumber(), order.getMemberId(), order.getOrderType(), order.getStatus(),
                order.getOrdererName(), order.getOrdererPhone(), order.getPickupName(), order.getPickupPhone(),
                order.getOriginalAmount(), order.getDiscountAmount(), order.getFinalAmount(), order.getPickupAt(),
                order.getPaymentExpiresAt(), isPaymentPending(order, now), order.getRequestMessage(), order.getRejectReason(),
                order.getCanceledAt(), order.getCancelReason(), order.getCreatedAt(),
                cancellationRetryAvailable || isCancellationRequestAvailable(order, now),
                itemViews
        );
    }

    private boolean isPaymentPending(Order order, LocalDateTime now) {
        return order.getStatus() == OrderStatus.PENDING_PAYMENT
                && order.getPaymentExpiresAt() != null
                && now.isBefore(order.getPaymentExpiresAt());
    }

    private boolean isCancellationRequestAvailable(Order order, LocalDateTime now) {
        return order.getOrderType() == OrderType.GENERAL
                && order.getStatus() == OrderStatus.READY_FOR_PICKUP
                && order.getPickupAt() != null
                && now.isBefore(order.getPickupAt());
    }

    private OrderDetailView.Item toItemView(
            OrderItem item,
            Map<Long, List<OrderItemOption>> optionsByItem,
            Map<Long, List<OrderItemImage>> imagesByItem
    ) {
        List<OrderDetailView.Option> options = optionsByItem.getOrDefault(item.getId(), List.of()).stream()
                .map(option -> new OrderDetailView.Option(
                        option.getOptionGroupName(), option.getOptionName(), option.getAdditionalPrice()))
                .toList();
        List<OrderDetailView.Image> images = imagesByItem.getOrDefault(item.getId(), List.of()).stream()
                .map(image -> new OrderDetailView.Image(image.getImageUrl(), image.getSortOrder()))
                .toList();
        return new OrderDetailView.Item(
                item.getId(), item.getProductId(), item.getProductName(), item.getProductType(), item.getQuantity(),
                item.getBasePrice(), item.getOptionAmount(), item.getTotalAmount(), item.getRequirements(), options, images
        );
    }
}
