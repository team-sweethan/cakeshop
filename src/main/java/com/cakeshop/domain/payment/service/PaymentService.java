package com.cakeshop.domain.payment.service;

import com.cakeshop.domain.order.service.OrderService;
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

/** READY 결제 조회와 승인 후 내부 결제 상태 확정을 담당한다. */
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentMapper paymentMapper;
    private final ProductStockService productStockService;
    private final OrderService orderService;

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
    }

    private void requireOneRow(int affectedRows) {
        if (affectedRows != 1) {
            throw new BusinessException(
                    PaymentErrorCode.PAYMENT_COMPLETE_FAILED
            );
        }
    }
}
