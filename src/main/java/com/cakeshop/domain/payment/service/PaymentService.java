package com.cakeshop.domain.payment.service;

import com.cakeshop.domain.order.service.OrderService;
import com.cakeshop.domain.coupon.service.CouponOrderCommandService;
import com.cakeshop.domain.order.service.OrderService.GeneralPaymentOrder;
import com.cakeshop.domain.order.service.OrderService.PaymentProduct;
import com.cakeshop.domain.payment.entity.Payment;
import com.cakeshop.domain.payment.error.PaymentErrorCode;
import com.cakeshop.domain.payment.infra.TossPaymentClient.ApprovalResult;
import com.cakeshop.domain.payment.mapper.PaymentMapper;
import com.cakeshop.domain.product.service.ProductStockService;
import com.cakeshop.global.error.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** READY 결제 조회와 승인 후 내부 결제 상태 확정을 담당한다. */
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentMapper paymentMapper;
    private final ProductStockService productStockService;
    private final OrderService orderService;
    private final PaymentRecoveryService paymentRecoveryService;
    // 쿠폰 담당자의 공개 계약으로 결제 성공 시 RESERVED 쿠폰을 USED로 확정한다.
    private final CouponOrderCommandService couponOrderCommandService;

    /** 주문의 현재 READY 결제를 조회한다. */
    @Transactional(readOnly = true)
    public Payment getReadyPayment(long orderId) {
        return paymentMapper.findReadyPaymentByOrderId(orderId)
                .orElseThrow(() -> new BusinessException(PaymentErrorCode.READY_PAYMENT_NOT_FOUND));
    }

    /** 중복 성공 콜백과 완료 화면 검증을 위해 완료 결제를 조회한다. */
    @Transactional(readOnly = true)
    public Optional<Payment> findDonePayment(long orderId) {
        return paymentMapper.findDonePaymentByOrderId(orderId);
    }

    /** 완료 화면에 필요한 DONE 결제를 조회한다. */
    @Transactional(readOnly = true)
    public Payment getDonePayment(long orderId) {
        return findDonePayment(orderId)
                .orElseThrow(() -> new BusinessException(
                        PaymentErrorCode.COMPLETED_PAYMENT_NOT_FOUND
                ));
    }

    /** 재고 차감, 결제 완료, 주문 상태 변경을 하나의 트랜잭션으로 확정한다. */
    @Transactional
    public void completeGeneralPayment(
            GeneralPaymentOrder order,
            Payment payment,
            ApprovalResult approval
    ) {
        // 주문 행을 먼저 잠가 스케줄러의 EXPIRED 전이와 동일한 잠금 순서를 사용한다.
        orderService.lockGeneralOrderForPayment(order.orderId());

        // products는 주문 생성 시점에 GENERAL로 저장된 주문 항목 스냅샷이다.
        for (PaymentProduct product : order.products()) {
            boolean stockDeducted = productStockService.decreaseStock(
                    product.productId(),
                    product.quantity()
            );
            if (stockDeducted) {
                orderService.recordGeneralStockDeduction(
                        product.orderItemId(),
                        approval.approvedAt()
                );
            }
        }

        requireOneRow(paymentMapper.completeIfReady(
                payment.getId(),
                approval.paymentKey(),
                approval.method(),
                approval.status(),
                approval.approvedAt()
        ));

        // 주문 상태 변경 쿼리는 같은 트랜잭션에서 DONE 결제를 확인한다.
        orderService.completeGeneralOrderAfterPayment(
                order.orderId(),
                approval.approvedAt()
        );
        // 주문·결제 완료가 같은 트랜잭션에서 성공한 뒤에만 쿠폰 사용을 확정한다.
        couponOrderCommandService.useReservedCouponForOrder(order.orderId());
        paymentRecoveryService.discardApprovalRecovery(payment);
    }

    /** PG 호출 없이 0원 주문의 재고·결제·주문·쿠폰 상태를 같은 트랜잭션에서 완료한다. */
    @Transactional
    public void completeZeroAmountGeneralPayment(
            GeneralPaymentOrder order,
            Payment payment,
            LocalDateTime completedAt
    ) {
        if (order.amount().compareTo(BigDecimal.ZERO) != 0
                || payment.getAmount() == null
                || payment.getAmount().compareTo(BigDecimal.ZERO) != 0) {
            throw new BusinessException(PaymentErrorCode.AMOUNT_MISMATCH);
        }

        orderService.lockGeneralOrderForPayment(order.orderId());
        for (PaymentProduct product : order.products()) {
            if (productStockService.decreaseStock(product.productId(), product.quantity())) {
                orderService.recordGeneralStockDeduction(product.orderItemId(), completedAt);
            }
        }
        requireOneRow(paymentMapper.completeZeroAmountIfReady(payment.getId(), completedAt));
        orderService.completeGeneralOrderAfterPayment(order.orderId(), completedAt);
        couponOrderCommandService.useReservedCouponForOrder(order.orderId());
    }

    private void requireOneRow(int affectedRows) {
        if (affectedRows != 1) {
            throw new BusinessException(
                    PaymentErrorCode.PAYMENT_COMPLETE_FAILED
            );
        }
    }
}
