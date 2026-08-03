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

/** 주문과 주문 항목 스냅샷의 저장·조회·조건부 상태 변경을 담당한다. */
@Mapper
public interface OrderMapper {

    /**
     * 주문 기본 정보를 저장하고 생성된 식별자를 {@code order.id}에 설정한다.
     *
     * @return 저장된 행 수
     */
    int insertOrder(Order order);

    /**
     * 주문 상품 스냅샷을 저장하고 생성된 식별자를 {@code orderItem.id}에 설정한다.
     *
     * @return 저장된 행 수
     */
    int insertOrderItem(OrderItem orderItem);

    /** 이미 저장된 주문 항목이 있는지 확인해 중복 주문 요청을 판별한다. */
    boolean existsOrderItemByOrderId(@Param("orderId") long orderId);

    /**
     * 주문 상품에 선택된 옵션 스냅샷 한 건을 저장한다.
     *
     * @return 저장된 행 수
     */
    int insertOrderItemOption(OrderItemOption option);

    /**
     * 주문제작 참고 이미지 한 건을 저장한다.
     *
     * @return 저장된 행 수
     */
    int insertOrderItemImage(OrderItemImage image);

    /** 주문 식별자로 주문 기본 정보를 조회한다. */
    Optional<Order> findOrderById(@Param("orderId") long orderId);

    /** 회원별 주문 생성 멱등키로 이미 생성된 주문을 조회한다. */
    Optional<Order> findOrderByMemberIdAndRequestKey(
            @Param("memberId") long memberId,
            @Param("requestKey") String requestKey
    );

    /** 취소 준비처럼 상태 전이와 경쟁하면 안 되는 작업에서 주문 행을 잠가 조회한다. */
    Optional<Order> findOrderByIdForUpdate(@Param("orderId") long orderId);

    /** 회원이 소유한 주문을 최근 생성 순서로 조회한다. */
    List<Order> findOrdersByMemberId(@Param("memberId") long memberId);

    /** 관리자 화면에 표시할 전체 주문을 최근 생성 순서로 조회한다. */
    List<Order> findAllOrders();

    /** 지정한 픽업일의 제작·픽업 대상 주문을 픽업 시각 순서로 조회한다. */
    List<Order> findFulfillmentOrders(
            @Param("pickupStart") LocalDateTime pickupStart,
            @Param("pickupEnd") LocalDateTime pickupEnd,
            @Param("status") OrderStatus status
    );

    /** 기준 시각까지 결제되지 않은 PENDING_PAYMENT 주문 식별자를 오래된 순으로 조회한다. */
    List<Long> findOverduePendingOrderIds(
            @Param("now") LocalDateTime now,
            @Param("limit") int limit
    );

    /** 주문이 존재하고 지정한 회원의 소유인지 확인한다. */
    boolean existsByIdAndMemberId(@Param("orderId") long orderId, @Param("memberId") long memberId);

    /** 주문에 포함된 상품 스냅샷을 저장 순서대로 조회한다. */
    List<OrderItem> findOrderItemsByOrderId(@Param("orderId") long orderId);

    /** 주문 상품에 포함된 옵션 스냅샷을 상품·옵션 순서대로 조회한다. */
    List<OrderItemOption> findOrderItemOptionsByOrderId(@Param("orderId") long orderId);

    /** 주문제작 참고 이미지를 상품·노출 순서대로 조회한다. */
    List<OrderItemImage> findOrderItemImagesByOrderId(@Param("orderId") long orderId);

    /** 결제 성공 시 실제로 차감한 주문 항목 재고를 한 번만 기록한다. */
    int markStockDeductedIfUnset(
            @Param("orderItemId") long orderItemId,
            @Param("deductedAt") LocalDateTime deductedAt
    );

    /** 취소 시 복구할 차감 이력을 잠그고 조회한다. */
    List<OrderItem> findStockDeductedItemsForRestore(@Param("orderId") long orderId);

    /** 차감됐고 아직 복구되지 않은 주문 항목을 복구 완료로 한 번만 기록한다. */
    int markStockRestoredIfDeducted(
            @Param("orderItemId") long orderItemId,
            @Param("restoredAt") LocalDateTime restoredAt
    );

    /**
     * DONE 결제가 있는 PENDING_PAYMENT 주문제작을 UNDER_REVIEW로 변경한다.
     *
     * @return 상태를 변경했으면 1, 조건이 맞지 않으면 0
     */
    int markUnderReviewAfterPaymentIfPending(
            @Param("orderId") long orderId,
            @Param("underReviewAt") LocalDateTime underReviewAt
    );

    /**
     * DONE 결제가 있는 PENDING_PAYMENT 일반 주문을 READY_FOR_PICKUP으로 변경한다.
     *
     * @return 상태를 변경했으면 1, 조건이 맞지 않으면 0
     */
    int markReadyForPickupAfterPaymentIfPending(
            @Param("orderId") long orderId,
            @Param("readyAt") LocalDateTime readyAt
    );

    /**
     * DONE 결제가 유지되는 UNDER_REVIEW 주문제작을 승인하고 픽업 대기 상태로 변경한다.
     *
     * @return 상태를 변경했으면 1, 조건이 맞지 않으면 0
     */
    int approveIfUnderReview(
            @Param("orderId") long orderId,
            @Param("approvedBy") long approvedBy,
            @Param("readyAt") LocalDateTime readyAt
    );

    /**
     * 결제가 CANCELED인 UNDER_REVIEW 주문제작을 반려하고 처리 정보를 기록한다.
     *
     * @return 상태를 변경했으면 1, 조건이 맞지 않으면 0
     */
    int rejectIfUnderReview(
            @Param("orderId") long orderId,
            @Param("rejectedBy") long rejectedBy,
            @Param("rejectedAt") LocalDateTime rejectedAt,
            @Param("rejectReason") String rejectReason
    );

    /**
     * DONE 결제가 유지되는 READY_FOR_PICKUP 주문을 PICKED_UP으로 변경한다.
     *
     * @return 상태를 변경했으면 1, 조건이 맞지 않으면 0
     */
    int markPickedUpIfReady(
            @Param("orderId") long orderId,
            @Param("pickedUpBy") long pickedUpBy,
            @Param("pickedUpAt") LocalDateTime pickedUpAt
    );

    /** 승인 뒤 내부 처리 실패로 PG 취소된 주문을 시스템 취소 상태로 변경한다. */
    int cancelAfterPaymentCompensation(
            @Param("orderId") long orderId,
            @Param("canceledAt") LocalDateTime canceledAt,
            @Param("cancelReason") String cancelReason
    );

    /**
     * PENDING_PAYMENT 주문과 연결된 READY 결제를 함께 EXPIRED로 변경한다.
     *
     * <p>결제 기한 도달 여부는 호출하는 Service가 먼저 검증해야 한다.</p>
     *
     * @return 주문 또는 결제를 변경했으면 양수, 조건이 맞지 않으면 0
     */
    int expireIfPendingPayment(
            @Param("orderId") long orderId,
            @Param("expiredAt") LocalDateTime expiredAt
    );

    /**
     * 결제가 CANCELED이고 취소 가능한 현재 상태가 일치하는 주문을 CANCELED로 변경한다.
     *
     * <p>일반 주문은 픽업 예정 시각 전의 READY_FOR_PICKUP 상태에서만 변경한다.</p>
     *
     * @return 상태를 변경했으면 1, 조건이 맞지 않으면 0
     */
    int cancelIfCurrent(
            @Param("orderId") long orderId,
            @Param("expectedStatus") OrderStatus expectedStatus,
            @Param("canceledBy") String canceledBy,
            @Param("cancelReason") String cancelReason,
            @Param("canceledAt") LocalDateTime canceledAt
    );
}
