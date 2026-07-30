package com.cakeshop.domain.payment.mapper;

import com.cakeshop.domain.payment.entity.Payment;
import com.cakeshop.domain.payment.entity.PaymentCancellation;
import com.cakeshop.domain.payment.entity.PaymentStatus;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Mapper
public interface PaymentMapper {

    // 결제 시도 생성. XML에서 상태를 READY로 고정한다.
    int insertReadyPayment(Payment payment);

    // 한 주문에서 발생한 모든 결제 시도를 조회한다.
    List<Payment> findPaymentsByOrderId(@Param("orderId") long orderId);

    // READY 결제의 승인 결과와 승인 시각을 함께 기록한다.
    int completeIfReady(
            @Param("paymentId") long paymentId,
            @Param("paymentKey") String paymentKey,
            @Param("method") String method,
            @Param("providerStatus") String providerStatus,
            @Param("approvedAt") LocalDateTime approvedAt
    );

    // READY 결제를 중단 처리하고 PG 상태와 실패 정보를 함께 기록한다.
    int abortIfReady(
            @Param("paymentId") long paymentId,
            @Param("providerStatus") String providerStatus,
            @Param("failureCode") String failureCode,
            @Param("failureMessage") String failureMessage
    );

    // READY 결제를 만료 처리하고 PG 상태와 실패 정보를 함께 기록한다.
    int expireIfReady(
            @Param("paymentId") long paymentId,
            @Param("providerStatus") String providerStatus,
            @Param("failureCode") String failureCode,
            @Param("failureMessage") String failureMessage
    );

    // 결제 취소 결과를 현재 결제 상태 조건과 함께 반영한다.
    int applyCancellationIfCurrent(
            @Param("paymentId") long paymentId,
            @Param("expectedStatus") PaymentStatus expectedStatus,
            @Param("nextStatus") PaymentStatus nextStatus,
            @Param("providerStatus") String providerStatus,
            @Param("canceledAt") LocalDateTime canceledAt
    );

    // 결제 취소·환불 요청을 REQUESTED 상태로 생성한다.
    int insertPaymentCancellation(PaymentCancellation cancellation);

    // 결제 취소·환불 요청을 ID로 조회한다.
    Optional<PaymentCancellation> findPaymentCancellationById(
            @Param("cancellationId") long cancellationId
    );

    // REQUESTED 환불 요청의 PG 거래키와 처리 완료 시각을 함께 기록한다.
    int completeCancellationIfRequested(
            @Param("cancellationId") long cancellationId,
            @Param("transactionKey") String transactionKey,
            @Param("canceledAt") LocalDateTime canceledAt
    );

    // REQUESTED 환불 요청의 실패 정보를 함께 기록한다.
    int failCancellationIfRequested(
            @Param("cancellationId") long cancellationId,
            @Param("failureCode") String failureCode,
            @Param("failureMessage") String failureMessage
    );
}
