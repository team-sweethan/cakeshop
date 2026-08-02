package com.cakeshop.domain.order.service;

import com.cakeshop.domain.order.dto.view.OrderDetailView;
import com.cakeshop.domain.order.dto.view.OrderListView;
import com.cakeshop.domain.order.entity.Order;
import com.cakeshop.domain.order.entity.OrderItem;
import com.cakeshop.domain.order.entity.OrderItemImage;
import com.cakeshop.domain.order.entity.OrderItemOption;
import com.cakeshop.domain.order.error.OrderErrorCode;
import com.cakeshop.domain.order.mapper.OrderMapper;
import com.cakeshop.global.error.BusinessException;
import com.cakeshop.global.error.CommonErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** 고객 소유권을 적용한 주문 조회와 관리자용 전체 주문 조회를 담당한다. */
@Service
@RequiredArgsConstructor
public class OrderQueryService {

    private final OrderMapper orderMapper;

    @Transactional(readOnly = true)
    public List<OrderListView> getMemberOrders(long memberId) {
        validateMemberId(memberId);
        return orderMapper.findOrdersByMemberId(memberId).stream()
                .map(this::toListView)
                .toList();
    }

    @Transactional(readOnly = true)
    public OrderDetailView getMemberOrder(long memberId, long orderId) {
        validateMemberId(memberId);
        Order order = findOrder(orderId);
        if (!Long.valueOf(memberId).equals(order.getMemberId())) {
            // 다른 회원에게 주문의 존재 여부도 노출하지 않는다.
            throw new BusinessException(CommonErrorCode.NOT_FOUND);
        }
        return toDetailView(order);
    }

    @Transactional(readOnly = true)
    public List<OrderListView> getAllOrdersForAdmin() {
        return orderMapper.findAllOrders().stream()
                .map(this::toListView)
                .toList();
    }

    @Transactional(readOnly = true)
    public OrderDetailView getOrderForAdmin(long orderId) {
        return toDetailView(findOrder(orderId));
    }

    private Order findOrder(long orderId) {
        if (orderId <= 0) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND);
        }
        return orderMapper.findOrderById(orderId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
    }

    private OrderListView toListView(Order order) {
        List<OrderItem> items = orderMapper.findOrderItemsByOrderId(order.getId());
        String productName = items.isEmpty() ? "주문 상품 없음" : items.getFirst().getProductName();
        return new OrderListView(
                order.getId(),
                order.getOrderNumber(),
                order.getMemberId(),
                order.getOrdererName(),
                order.getOrderType(),
                order.getStatus(),
                productName,
                items.size(),
                order.getFinalAmount(),
                order.getPickupAt(),
                order.getCreatedAt()
        );
    }

    private OrderDetailView toDetailView(Order order) {
        List<OrderItem> items = orderMapper.findOrderItemsByOrderId(order.getId());
        Map<Long, List<OrderItemOption>> optionsByItem = orderMapper
                .findOrderItemOptionsByOrderId(order.getId())
                .stream()
                .collect(Collectors.groupingBy(OrderItemOption::getOrderItemId));
        Map<Long, List<OrderItemImage>> imagesByItem = orderMapper
                .findOrderItemImagesByOrderId(order.getId())
                .stream()
                .collect(Collectors.groupingBy(OrderItemImage::getOrderItemId));

        List<OrderDetailView.Item> itemViews = items.stream()
                .map(item -> toItemView(item, optionsByItem, imagesByItem))
                .toList();

        return new OrderDetailView(
                order.getId(),
                order.getOrderNumber(),
                order.getMemberId(),
                order.getOrderType(),
                order.getStatus(),
                order.getOrdererName(),
                order.getOrdererPhone(),
                order.getPickupName(),
                order.getPickupPhone(),
                order.getOriginalAmount(),
                order.getDiscountAmount(),
                order.getFinalAmount(),
                order.getPickupAt(),
                order.getPaymentExpiresAt(),
                order.getRequestMessage(),
                order.getRejectReason(),
                order.getCanceledAt(),
                order.getCancelReason(),
                order.getCreatedAt(),
                itemViews
        );
    }

    private OrderDetailView.Item toItemView(
            OrderItem item,
            Map<Long, List<OrderItemOption>> optionsByItem,
            Map<Long, List<OrderItemImage>> imagesByItem
    ) {
        List<OrderDetailView.Option> options = optionsByItem
                .getOrDefault(item.getId(), List.of())
                .stream()
                .map(option -> new OrderDetailView.Option(
                        option.getOptionGroupName(),
                        option.getOptionName(),
                        option.getAdditionalPrice()
                ))
                .toList();
        List<OrderDetailView.Image> images = imagesByItem
                .getOrDefault(item.getId(), List.of())
                .stream()
                .map(image -> new OrderDetailView.Image(
                        image.getImageUrl(),
                        image.getSortOrder()
                ))
                .toList();

        return new OrderDetailView.Item(
                item.getId(),
                item.getProductId(),
                item.getProductName(),
                item.getProductType(),
                item.getQuantity(),
                item.getBasePrice(),
                item.getOptionAmount(),
                item.getTotalAmount(),
                item.getRequirements(),
                options,
                images
        );
    }

    private void validateMemberId(long memberId) {
        if (memberId <= 0) {
            throw new BusinessException(OrderErrorCode.MEMBER_NOT_AVAILABLE);
        }
    }
}
