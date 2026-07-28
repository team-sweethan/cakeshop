package com.cakeshop.domain.order.mapper;

import com.cakeshop.domain.order.entity.Order;
import com.cakeshop.domain.order.entity.OrderItem;
import com.cakeshop.domain.order.entity.OrderItemImage;
import com.cakeshop.domain.order.entity.OrderItemOption;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

@Mapper
public interface OrderMapper {
    // TODO: 조회·저장 메서드 — LIMIT #{size} OFFSET #{offset} 페이징 규칙 준수

    // 주문 생성
    int insertOrder(Order order);

    // orde_items에 주문에 포함된 상품 1개 저장.
    int insertOrderItem(OrderItem orderItem);

    //주문 상품에 선택된 옵션 하나 order_item_options에 저장.
    int insertOrderItemOption(OrderItemOption option);

    // [주문 상품] 주문 제작의 참고 이미지 1개 order_item_image에 저장.
    int insertOrderItemImage(OrderItemImage image);

    Optional<Order> findOrderById(@Param("orderId") long orderId);

    List<OrderItem> findOrderItemsByOrderId(@Param("orderId") long orderId);

    List<OrderItemOption> findOrderItemOptionsByOrderId(@Param("orderId") long orderId);

    List<OrderItemImage> findOrderItemImagesByOrderId(@Param("orderId") long orderId);
}
