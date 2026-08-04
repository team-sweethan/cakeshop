package com.cakeshop.domain.payment.service;

import com.cakeshop.domain.member.service.MemberService;
import com.cakeshop.domain.order.entity.Order;
import com.cakeshop.domain.order.entity.OrderItem;
import com.cakeshop.domain.order.entity.OrderStatus;
import com.cakeshop.domain.order.entity.OrderType;
import com.cakeshop.domain.order.error.OrderErrorCode;
import com.cakeshop.domain.order.mapper.OrderMapper;
import com.cakeshop.domain.payment.entity.Payment;
import com.cakeshop.domain.payment.entity.PaymentCancellation;
import com.cakeshop.domain.payment.entity.PaymentCancellationStatus;
import com.cakeshop.domain.payment.error.PaymentErrorCode;
import com.cakeshop.domain.payment.infra.TossPaymentClient.CancellationResult;
import com.cakeshop.domain.payment.mapper.PaymentMapper;
import com.cakeshop.domain.product.service.ProductStockService;
import com.cakeshop.global.error.BusinessException;
import com.cakeshop.global.error.CommonErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** 전체 결제 취소 요청과 내부 주문·결제·재고 완료 처리를 담당한다. */
@Service
@RequiredArgsConstructor
public class RefundService {

    private static final String CUSTOMER = "CUSTOMER";
    private static final String ADMIN = "ADMIN";

    private final OrderMapper orderMapper;
    private final PaymentMapper paymentMapper;
    private final ProductStockService productStockService;
    private final MemberService memberService;
    private final Clock clock;

    /** 회원 소유권과 현재 상태를 검증하고 PG 호출 전에 취소 요청을 저장한다. */
    @Transactional
    public RefundRequest prepareCustomerCancellation(long memberId, long orderId, String reason) {
        validateCancellationInput(memberId, reason);
        validateActiveMember(memberId);
        Order order = findOwnedOrder(memberId, orderId);
        return prepareCancellation(memberId, order, reason, CUSTOMER);
    }

    /** 인증된 관리자 식별자와 현재 상태를 검증하고 일반 상품 취소 요청을 저장한다. */
    @Transactional
    public RefundRequest prepareAdminCancellation(long adminMemberId, long orderId, String reason) {
        validateCancellationInput(adminMemberId, reason);
        Order order = orderMapper.findOrderByIdForUpdate(orderId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        return prepareCancellation(adminMemberId, order, reason, ADMIN);
    }

    private RefundRequest prepareCancellation(
            long requestedBy,
            Order order,
            String reason,
            String canceledBy
    ) {
        LocalDateTime now = LocalDateTime.now(clock);
        requireGeneralReadyForPickup(order);
        Payment payment = paymentMapper.findDonePaymentByOrderId(order.getId())
                .orElseThrow(() -> new BusinessException(PaymentErrorCode.PAYMENT_CANCEL_NOT_AVAILABLE));
        if (payment.getPaymentKey() == null || payment.getPaymentKey().isBlank()) {
            throw new BusinessException(PaymentErrorCode.PAYMENT_CANCEL_NOT_AVAILABLE);
        }

        PaymentCancellation requestedCancellation = paymentMapper
                .findRequestedCancellationByPaymentId(payment.getId())
                .orElse(null);
        if (requestedCancellation != null) {
            OrderStatus expectedStatus = requireGeneralCancelableStatus(
                    order,
                    requestedCancellation.getRequestedAt()
            );
            return reuseRequestedCancellation(
                    requestedCancellation,
                    payment,
                    order,
                    expectedStatus,
                    requestedBy,
                    canceledBy
            );
        }

        OrderStatus expectedStatus = requireGeneralCancelableStatus(order, now);
        PaymentCancellation cancellation = new PaymentCancellation();
        cancellation.setPaymentId(payment.getId());
        cancellation.setIdempotencyKey("CANCEL-" + UUID.randomUUID());
        cancellation.setCancelAmount(payment.getAmount());
        cancellation.setCancelReason(reason.trim());
        cancellation.setRequestType(canceledBy);
        cancellation.setRequestedBy(requestedBy);
        if (paymentMapper.insertPaymentCancellation(cancellation) != 1) {
            throw new BusinessException(PaymentErrorCode.PAYMENT_CANCEL_NOT_AVAILABLE);
        }

        return new RefundRequest(
                cancellation.getId(),
                order.getId(),
                expectedStatus,
                payment.getPaymentKey(),
                cancellation.getIdempotencyKey(),
                cancellation.getCancelReason(),
                canceledBy,
                now
        );
    }

    private RefundRequest reuseRequestedCancellation(
            PaymentCancellation cancellation,
            Payment payment,
            Order order,
            OrderStatus expectedStatus,
            long requestedBy,
            String canceledBy
    ) {
        if (cancellation.getStatus() != PaymentCancellationStatus.REQUESTED
                || !Long.valueOf(payment.getId()).equals(cancellation.getPaymentId())
                || !canceledBy.equals(cancellation.getRequestType())
                || !Long.valueOf(requestedBy).equals(cancellation.getRequestedBy())
                || cancellation.getId() == null
                || cancellation.getIdempotencyKey() == null
                || cancellation.getIdempotencyKey().isBlank()
                || cancellation.getCancelReason() == null
                || cancellation.getCancelReason().isBlank()
                || cancellation.getRequestedAt() == null) {
            throw new BusinessException(PaymentErrorCode.PAYMENT_CANCEL_NOT_AVAILABLE);
        }
        return new RefundRequest(
                cancellation.getId(),
                order.getId(),
                expectedStatus,
                payment.getPaymentKey(),
                cancellation.getIdempotencyKey(),
                cancellation.getCancelReason(),
                canceledBy,
                cancellation.getRequestedAt()
        );
    }

    /** PG 취소 성공 뒤 결제·주문 상태와 실제 차감 재고 복구를 한 트랜잭션으로 완료한다. */
    @Transactional
    public void completeCancellation(RefundRequest request, CancellationResult result) {
        if (request == null || result == null || !"CANCELED".equals(result.status())) {
            throw new BusinessException(PaymentErrorCode.PAYMENT_CANCEL_COMPLETE_FAILED);
        }
        PaymentCancellation cancellation = paymentMapper
                .findPaymentCancellationById(request.cancellationId())
                .orElseThrow(() -> new BusinessException(PaymentErrorCode.PAYMENT_CANCEL_COMPLETE_FAILED));
        if (cancellation.getStatus() != PaymentCancellationStatus.REQUESTED) {
            throw new BusinessException(PaymentErrorCode.PAYMENT_CANCEL_COMPLETE_FAILED);
        }
        Payment payment = paymentMapper.findPaymentById(cancellation.getPaymentId())
                .orElseThrow(() -> new BusinessException(PaymentErrorCode.PAYMENT_CANCEL_COMPLETE_FAILED));
        if (!Long.valueOf(request.orderId()).equals(payment.getOrderId())) {
            throw new BusinessException(PaymentErrorCode.PAYMENT_CANCEL_COMPLETE_FAILED);
        }

        Order order = orderMapper.findOrderById(request.orderId())
                .orElseThrow(() -> new BusinessException(PaymentErrorCode.PAYMENT_CANCEL_COMPLETE_FAILED));
        LocalDateTime canceledAt = result.canceledAt();
        OrderStatus currentExpectedStatus = requireGeneralCancelableStatus(
                order,
                request.requestedAt()
        );
        if (currentExpectedStatus != request.expectedStatus()) {
            throw new BusinessException(PaymentErrorCode.PAYMENT_CANCEL_COMPLETE_FAILED);
        }

        requirePositive(paymentMapper.completeCancellationIfRequested(
                cancellation.getId(),
                result.transactionKey(),
                canceledAt
        ));
        requireOneRow(orderMapper.cancelIfCurrent(
                order.getId(),
                currentExpectedStatus,
                request.canceledBy(),
                cancellation.getCancelReason(),
                request.requestedAt()
        ));
        restoreDeductedStock(order, canceledAt);
    }

    @Transactional
    public void failRequestedCancellation(long cancellationId) {
        paymentMapper.failCancellationIfRequested(
                cancellationId,
                "TOSS_CANCEL_FAILED",
                "결제 취소 요청에 실패했습니다."
        );
    }

    private Order findOwnedOrder(long memberId, long orderId) {
        Order order = orderMapper.findOrderByIdForUpdate(orderId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        if (!Long.valueOf(memberId).equals(order.getMemberId())) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND);
        }
        return order;
    }

    private OrderStatus requireGeneralCancelableStatus(Order order, LocalDateTime canceledAt) {
        requireGeneralReadyForPickup(order);
        if (canceledAt != null && canceledAt.isBefore(order.getPickupAt())) {
            return OrderStatus.READY_FOR_PICKUP;
        }
        throw new BusinessException(PaymentErrorCode.PAYMENT_CANCEL_NOT_AVAILABLE);
    }

    private void requireGeneralReadyForPickup(Order order) {
        if (order.getOrderType() != OrderType.GENERAL
                || order.getStatus() != OrderStatus.READY_FOR_PICKUP
                || order.getPickupAt() == null) {
            throw new BusinessException(PaymentErrorCode.PAYMENT_CANCEL_NOT_AVAILABLE);
        }
    }

    private void validateCancellationInput(long requestedBy, String reason) {
        if (requestedBy <= 0 || reason == null || reason.isBlank() || reason.length() > 200) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }
    }

    private void validateActiveMember(long memberId) {
        if (!memberService.isActiveMember(memberId)) {
            throw new BusinessException(OrderErrorCode.MEMBER_NOT_AVAILABLE);
        }
    }

    private void restoreDeductedStock(Order order, LocalDateTime restoredAt) {
        if (order.getOrderType() != OrderType.GENERAL) {
            return;
        }
        List<OrderItem> items = orderMapper.findStockDeductedItemsForRestore(order.getId());
        for (OrderItem item : items) {
            if (item.getProductId() == null || item.getQuantity() == null || item.getQuantity() <= 0) {
                throw new BusinessException(PaymentErrorCode.PAYMENT_CANCEL_COMPLETE_FAILED);
            }
            productStockService.restoreStock(item.getProductId(), item.getQuantity());
            requireOneRow(orderMapper.markStockRestoredIfDeducted(item.getId(), restoredAt));
        }
    }

    private void requireOneRow(int affectedRows) {
        if (affectedRows != 1) {
            throw new BusinessException(PaymentErrorCode.PAYMENT_CANCEL_COMPLETE_FAILED);
        }
    }

    private void requirePositive(int affectedRows) {
        if (affectedRows <= 0) {
            throw new BusinessException(PaymentErrorCode.PAYMENT_CANCEL_COMPLETE_FAILED);
        }
    }

    public record RefundRequest(
            long cancellationId,
            long orderId,
            OrderStatus expectedStatus,
            String paymentKey,
            String idempotencyKey,
            String reason,
            String canceledBy,
            LocalDateTime requestedAt
    ) {
    }
}
