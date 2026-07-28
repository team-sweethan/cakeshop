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

    int insertOrder(Order order);

    int insertOrderItem(OrderItem orderItem);

    int insertOrderItemOption(OrderItemOption option);

    int insertOrderItemImage(OrderItemImage image);

    Optional<Order> findOrderById(@Param("orderId") long orderId);

    List<OrderItem> findOrderItemsByOrderId(@Param("orderId") long orderId);

    List<OrderItemOption> findOrderItemOptionsByOrderId(@Param("orderId") long orderId);

    List<OrderItemImage> findOrderItemImagesByOrderId(@Param("orderId") long orderId);
}
