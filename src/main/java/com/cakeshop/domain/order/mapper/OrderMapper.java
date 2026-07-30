package com.cakeshop.domain.order.mapper;

import com.cakeshop.domain.order.entity.Order;
import com.cakeshop.domain.order.entity.OrderItem;
import com.cakeshop.domain.order.entity.OrderItemImage;
import com.cakeshop.domain.order.entity.OrderItemOption;
import com.cakeshop.domain.order.entity.OrderStatus;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
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

    // 결제가 끝난 주문 제작 주문을 관리자 검토 상태로 전이한다.
    int markUnderReviewAfterPaymentIfPending(
            @Param("orderId") long orderId,
            @Param("underReviewAt") LocalDateTime underReviewAt
    );

    // 결제가 끝난 일반 주문을 픽업 대기 상태로 전이한다.
    int markReadyForPickupAfterPaymentIfPending(
            @Param("orderId") long orderId,
            @Param("readyAt") LocalDateTime readyAt
    );

    // 검토 중인 주문 제작 주문을 승인하고 처리자와 처리 시각을 기록한다.
    int approveIfUnderReview(
            @Param("orderId") long orderId,
            @Param("approvedBy") long approvedBy,
            @Param("readyAt") LocalDateTime readyAt
    );

    // 검토 중인 주문 제작 주문을 반려하고 사유·처리자·처리 시각을 기록한다.
    int rejectIfUnderReview(
            @Param("orderId") long orderId,
            @Param("rejectedBy") long rejectedBy,
            @Param("rejectedAt") LocalDateTime rejectedAt,
            @Param("rejectReason") String rejectReason
    );

    // 픽업 대기 주문의 수령 완료 시각과 처리자를 기록한다.
    int markPickedUpIfReady(
            @Param("orderId") long orderId,
            @Param("pickedUpBy") long pickedUpBy,
            @Param("pickedUpAt") LocalDateTime pickedUpAt
    );

    // 결제 대기 주문을 만료 처리한다.
    int expireIfPendingPayment(
            @Param("orderId") long orderId,
            @Param("expiredAt") LocalDateTime expiredAt
    );

    // 취소 가능한 현재 상태가 일치할 때 취소 주체·사유·처리 시각을 함께 기록한다.
    int cancelIfCurrent(
            @Param("orderId") long orderId,
            @Param("expectedStatus") OrderStatus expectedStatus,
            @Param("canceledBy") String canceledBy,
            @Param("cancelReason") String cancelReason,
            @Param("canceledAt") LocalDateTime canceledAt
    );
}
