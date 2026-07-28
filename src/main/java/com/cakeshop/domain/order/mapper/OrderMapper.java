package com.cakeshop.domain.order.mapper;

import com.cakeshop.domain.order.entity.*;
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

    // 주문 단건 조회
    Optional<Order> findOrderById(@Param("orderId") long orderId);

    // 주문이 해당 회원의 소유인지 확인
    boolean existsByIdAndMemberId(@Param("orderId") long orderId, @Param("memberId") long memberId);

    List<OrderItem> findOrderItemsByOrderId(@Param("orderId") long orderId);

    List<OrderItemOption> findOrderItemOptionsByOrderId(@Param("orderId") long orderId);

    List<OrderItemImage> findOrderItemImagesByOrderId(@Param("orderId") long orderId);

    // 현재 상태 조건부 변경
    int updateStatusIfCurrent(
            @Param("orderId") long orderId,
            @Param("expectedStatus") OrderStatus expectedStatus,
            @Param("nextStatus") OrderStatus nextStatus
    );
}
