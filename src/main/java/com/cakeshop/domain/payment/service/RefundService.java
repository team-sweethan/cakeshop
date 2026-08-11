package com.cakeshop.domain.payment.service;

import com.cakeshop.domain.member.service.MemberService;
import com.cakeshop.domain.order.error.OrderErrorCode;
import com.cakeshop.domain.order.service.OrderPaymentCancellationCommandService;
import com.cakeshop.domain.payment.entity.Payment;
import com.cakeshop.domain.payment.entity.PaymentCancellation;
import com.cakeshop.domain.payment.entity.PaymentCancellationStatus;
import com.cakeshop.domain.payment.entity.PaymentStatus;
import com.cakeshop.domain.payment.error.PaymentErrorCode;
import com.cakeshop.domain.payment.infra.TossPaymentClient.CancellationResult;
import com.cakeshop.domain.payment.mapper.PaymentMapper;
import com.cakeshop.global.error.BusinessException;
import com.cakeshop.global.error.CommonErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** 전체 결제 취소 요청과 내부 주문·결제·재고 완료 처리를 담당한다. */
@Service
@RequiredArgsConstructor
public class RefundService {

    private static final String CUSTOMER = "CUSTOMER";
    private static final String ADMIN = "ADMIN";

    private final PaymentMapper paymentMapper;
    private final OrderPaymentCancellationCommandService orderPaymentCancellationCommandService;
    private final MemberService memberService;
    private final Clock clock;

    /** 회원 소유권과 현재 상태를 검증하고 PG 호출 전에 취소 요청을 저장한다. */
    @Transactional
    public RefundRequest prepareCustomerCancellation(long memberId, long orderId, String reason) {
        validateCancellationInput(memberId, reason);
        validateActiveMember(memberId);
        orderPaymentCancellationCommandService.lockCustomerOrderForPaymentCancellation(memberId, orderId);
        return prepareCancellation(memberId, orderId, reason, CUSTOMER);
    }

    /** 인증된 관리자 식별자와 현재 상태를 검증하고 일반 상품 취소 요청을 저장한다. */
    @Transactional
    public RefundRequest prepareAdminCancellation(long adminMemberId, long orderId, String reason) {
        validateCancellationInput(adminMemberId, reason);
        orderPaymentCancellationCommandService.lockOrderForPaymentCancellation(orderId);
        return prepareCancellation(adminMemberId, orderId, reason, ADMIN);
    }

    /**
     * PG 승인 없이 완료된 0원 일반 주문을 내부 상태 전이만으로 취소한다.
     * 일반 금액 주문이면 기존 PG 취소 흐름을 사용하도록 false를 반환한다.
     */
    @Transactional
    public boolean cancelCustomerZeroAmountOrder(long memberId, long orderId, String reason) {
        validateCancellationInput(memberId, reason);
        validateActiveMember(memberId);
        orderPaymentCancellationCommandService.lockCustomerOrderForPaymentCancellation(memberId, orderId);
        return cancelZeroAmountOrder(orderId, reason, CUSTOMER);
    }

    /** 관리자가 요청한 0원 일반 주문 취소를 PG 호출 없이 완료한다. */
    @Transactional
    public boolean cancelAdminZeroAmountOrder(long adminMemberId, long orderId, String reason) {
        validateCancellationInput(adminMemberId, reason);
        orderPaymentCancellationCommandService.lockOrderForPaymentCancellation(orderId);
        return cancelZeroAmountOrder(orderId, reason, ADMIN);
    }

    private RefundRequest prepareCancellation(
            long requestedBy,
            long orderId,
            String reason,
            String canceledBy
    ) {
        LocalDateTime now = LocalDateTime.now(clock);
        Payment payment = paymentMapper.findDonePaymentByOrderId(orderId)
                .orElseThrow(() -> new BusinessException(PaymentErrorCode.PAYMENT_CANCEL_NOT_AVAILABLE));
        if (payment.getPaymentKey() == null || payment.getPaymentKey().isBlank()) {
            throw new BusinessException(PaymentErrorCode.PAYMENT_CANCEL_NOT_AVAILABLE);
        }

        PaymentCancellation requestedCancellation = paymentMapper
                .findRequestedCancellationByPaymentId(payment.getId())
                .orElse(null);
        if (requestedCancellation != null) {
            return reuseRequestedCancellation(
                    requestedCancellation,
                    payment,
                    orderId
            );
        }

        requireGeneralPaymentCancellationAvailable(orderId, now);
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
                orderId,
                payment.getPaymentKey(),
                cancellation.getIdempotencyKey(),
                cancellation.getCancelReason(),
                canceledBy,
                now
        );
    }

    private boolean cancelZeroAmountOrder(long orderId, String reason, String canceledBy) {
        Payment payment = paymentMapper.findDonePaymentByOrderId(orderId)
                .orElseThrow(() -> new BusinessException(PaymentErrorCode.PAYMENT_CANCEL_NOT_AVAILABLE));
        if (payment.getAmount() == null || payment.getAmount().signum() != 0) {
            return false;
        }

        LocalDateTime canceledAt = LocalDateTime.now(clock);
        requireGeneralPaymentCancellationAvailable(orderId, canceledAt);
        requireOneRow(paymentMapper.cancelIfDone(payment.getId(), "ZERO_AMOUNT_CANCELED", canceledAt));
        completeOrderCancellation(orderId, canceledBy, reason.trim(), canceledAt, canceledAt);
        return true;
    }

    private RefundRequest reuseRequestedCancellation(
            PaymentCancellation cancellation,
            Payment payment,
            long orderId
    ) {
        if (cancellation.getStatus() != PaymentCancellationStatus.REQUESTED
                || !Long.valueOf(payment.getId()).equals(cancellation.getPaymentId())
                || cancellation.getId() == null
                || cancellation.getIdempotencyKey() == null
                || cancellation.getIdempotencyKey().isBlank()
                || cancellation.getCancelReason() == null
                || cancellation.getCancelReason().isBlank()
                || cancellation.getRequestType() == null
                || cancellation.getRequestType().isBlank()
                || cancellation.getRequestedAt() == null) {
            throw new BusinessException(PaymentErrorCode.PAYMENT_CANCEL_NOT_AVAILABLE);
        }
        requireGeneralPaymentCancellationAvailable(orderId, cancellation.getRequestedAt());
        return new RefundRequest(
                cancellation.getId(),
                orderId,
                payment.getPaymentKey(),
                cancellation.getIdempotencyKey(),
                cancellation.getCancelReason(),
                cancellation.getRequestType(),
                cancellation.getRequestedAt()
        );
    }

    /** PG 취소 성공 뒤 결제·주문 상태와 실제 차감 재고 복구를 한 트랜잭션으로 완료한다. */
    @Transactional
    public void completeCancellation(RefundRequest request, CancellationResult result) {
        if (request == null
                || result == null
                || !"CANCELED".equals(result.status())
                || result.transactionKey() == null
                || result.transactionKey().isBlank()
                || result.canceledAt() == null) {
            throw new BusinessException(PaymentErrorCode.PAYMENT_CANCEL_COMPLETE_FAILED);
        }
        PaymentCancellation cancellation = paymentMapper
                .findPaymentCancellationByIdForUpdate(request.cancellationId())
                .orElseThrow(() -> new BusinessException(PaymentErrorCode.PAYMENT_CANCEL_COMPLETE_FAILED));
        Payment payment = paymentMapper.findPaymentById(cancellation.getPaymentId())
                .orElseThrow(() -> new BusinessException(PaymentErrorCode.PAYMENT_CANCEL_COMPLETE_FAILED));
        if (!Long.valueOf(request.orderId()).equals(payment.getOrderId())
                || !Objects.equals(request.paymentKey(), payment.getPaymentKey())
                || !Objects.equals(request.idempotencyKey(), cancellation.getIdempotencyKey())
                || !Objects.equals(request.reason(), cancellation.getCancelReason())
                || !Objects.equals(request.canceledBy(), cancellation.getRequestType())) {
            throw new BusinessException(PaymentErrorCode.PAYMENT_CANCEL_COMPLETE_FAILED);
        }

        if (cancellation.getStatus() == PaymentCancellationStatus.DONE) {
            if (payment.getStatus() != PaymentStatus.CANCELED
                    || !orderPaymentCancellationCommandService.isGeneralPaymentCancellationCompleted(request.orderId())
                    || !Objects.equals(cancellation.getTransactionKey(), result.transactionKey())) {
                throw new BusinessException(PaymentErrorCode.PAYMENT_CANCEL_COMPLETE_FAILED);
            }
            completeOrderCancellation(
                    request.orderId(),
                    request.canceledBy(),
                    cancellation.getCancelReason(),
                    request.requestedAt(),
                    result.canceledAt()
            );
            return;
        }
        if (cancellation.getStatus() != PaymentCancellationStatus.REQUESTED) {
            throw new BusinessException(PaymentErrorCode.PAYMENT_CANCEL_COMPLETE_FAILED);
        }
        LocalDateTime canceledAt = result.canceledAt();
        requirePositive(paymentMapper.completeCancellationIfRequested(
                cancellation.getId(),
                result.transactionKey(),
                canceledAt
        ));
        completeOrderCancellation(
                request.orderId(),
                request.canceledBy(),
                cancellation.getCancelReason(),
                request.requestedAt(),
                canceledAt
        );
    }

    @Transactional
    public void failRequestedCancellation(long cancellationId) {
        paymentMapper.failCancellationIfRequested(
                cancellationId,
                "TOSS_CANCEL_FAILED",
                "결제 취소 요청에 실패했습니다."
        );
    }

    /** PG 오류로 남은 고객·관리자 취소 요청을 같은 멱등키로 재처리한다. */
    @Transactional(readOnly = true)
    public List<RefundRequest> getRequestedCancellations(int limit) {
        if (limit <= 0) {
            throw new BusinessException(PaymentErrorCode.PAYMENT_RECOVERY_PENDING);
        }
        return paymentMapper.findRequestedRefundCancellations(limit).stream()
                .map(this::toRefundRequest)
                .toList();
    }

    private RefundRequest toRefundRequest(PaymentCancellation cancellation) {
        Payment payment = paymentMapper.findPaymentById(cancellation.getPaymentId())
                .orElseThrow(() -> new BusinessException(PaymentErrorCode.PAYMENT_RECOVERY_PENDING));
        requireGeneralPaymentCancellationAvailable(payment.getOrderId(), cancellation.getRequestedAt());
        return reuseRequestedCancellation(cancellation, payment, payment.getOrderId());
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

    private void requireGeneralPaymentCancellationAvailable(long orderId, LocalDateTime requestedAt) {
        if (!orderPaymentCancellationCommandService.isGeneralPaymentCancellationAvailable(orderId, requestedAt)) {
            throw new BusinessException(PaymentErrorCode.PAYMENT_CANCEL_NOT_AVAILABLE);
        }
    }

    private void completeOrderCancellation(
            long orderId,
            String canceledBy,
            String reason,
            LocalDateTime requestedAt,
            LocalDateTime canceledAt
    ) {
        if (!orderPaymentCancellationCommandService.completeGeneralPaymentCancellation(
                orderId, canceledBy, reason, requestedAt, canceledAt
        )) {
            throw new BusinessException(PaymentErrorCode.PAYMENT_CANCEL_COMPLETE_FAILED);
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
            String paymentKey,
            String idempotencyKey,
            String reason,
            String canceledBy,
            LocalDateTime requestedAt
    ) {
    }
}
