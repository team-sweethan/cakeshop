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

    // DONE 결제가 있는 주문 제작 주문을 관리자 검토 상태로 전이한다.
    int markUnderReviewAfterPaymentIfPending(
            @Param("orderId") long orderId,
            @Param("underReviewAt") LocalDateTime underReviewAt
    );

    // DONE 결제가 있는 일반 주문을 픽업 대기 상태로 전이한다.
    int markReadyForPickupAfterPaymentIfPending(
            @Param("orderId") long orderId,
            @Param("readyAt") LocalDateTime readyAt
    );

    // DONE 결제가 유지되는 검토 중 주문 제작을 승인하고 처리 정보를 기록한다.
    int approveIfUnderReview(
            @Param("orderId") long orderId,
            @Param("approvedBy") long approvedBy,
            @Param("readyAt") LocalDateTime readyAt
    );

    // 결제 취소가 끝난 검토 중 주문 제작을 반려하고 처리 정보를 기록한다.
    int rejectIfUnderReview(
            @Param("orderId") long orderId,
            @Param("rejectedBy") long rejectedBy,
            @Param("rejectedAt") LocalDateTime rejectedAt,
            @Param("rejectReason") String rejectReason
    );

    // DONE 결제가 유지되는 픽업 대기 주문의 수령 완료 정보를 기록한다.
    int markPickedUpIfReady(
            @Param("orderId") long orderId,
            @Param("pickedUpBy") long pickedUpBy,
            @Param("pickedUpAt") LocalDateTime pickedUpAt
    );

    // 결제 대기 주문과 연결된 READY 결제를 함께 만료 처리한다.
    int expireIfPendingPayment(
            @Param("orderId") long orderId,
            @Param("expiredAt") LocalDateTime expiredAt
    );

    // 결제 취소가 끝나고 취소 가능한 현재 상태가 일치할 때 주문을 취소한다.
    int cancelIfCurrent(
            @Param("orderId") long orderId,
            @Param("expectedStatus") OrderStatus expectedStatus,
            @Param("canceledBy") String canceledBy,
            @Param("cancelReason") String cancelReason,
            @Param("canceledAt") LocalDateTime canceledAt
    );
}
