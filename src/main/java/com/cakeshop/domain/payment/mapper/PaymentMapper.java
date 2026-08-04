package com.cakeshop.domain.payment.mapper;

import com.cakeshop.domain.payment.entity.Payment;
import com.cakeshop.domain.payment.entity.PaymentCancellation;
import com.cakeshop.domain.payment.entity.PaymentStatus;
import com.cakeshop.domain.payment.dto.view.PaymentAdminListRow;
import com.cakeshop.domain.payment.dto.view.PaymentAdminSummaryView;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Mapper
public interface PaymentMapper {

    // 관리자 화면에서 결제 상태별 실제 결제 시도와 최근 취소 요청 결과를 조회한다.
    List<PaymentAdminListRow> findPaymentsForAdmin(
            @Param("status") PaymentStatus status
    );

    // 관리자 결제 화면의 전체 상태별 건수를 집계한다.
    PaymentAdminSummaryView summarizePaymentsForAdmin();

    // 결제 대기 주문에 금액이 일치하는 결제 시도를 생성하고 상태를 READY로 고정한다.
    int insertReadyPayment(Payment payment);

    // 한 주문에서 발생한 모든 결제 시도를 조회한다.
    List<Payment> findPaymentsByOrderId(@Param("orderId") long orderId);

    // 결제 ID로 한 건을 조회한다.
    Optional<Payment> findPaymentById(@Param("paymentId") long paymentId);

    // 주문의 현재 DONE 결제를 조회한다. DB 제약에 따라 최대 한 건이다.
    Optional<Payment> findDonePaymentByOrderId(@Param("orderId") long orderId);

    // 주문의 현재 READY 결제 시도를 조회한다. DB 제약에 따라 최대 한 건이다.
    Optional<Payment> findReadyPaymentByOrderId(@Param("orderId") long orderId);

    // 결제 대기 주문의 READY 결제에만 승인 결과와 승인 시각을 함께 기록한다.
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

    // DONE 결제의 전체 취소 결과와 취소 시각을 함께 기록한다.
    int cancelIfDone(
            @Param("paymentId") long paymentId,
            @Param("providerStatus") String providerStatus,
            @Param("canceledAt") LocalDateTime canceledAt
    );

    // 결제 취소·환불 요청을 REQUESTED 상태로 생성한다.
    int insertPaymentCancellation(PaymentCancellation cancellation);

    // 결제 취소·환불 요청을 ID로 조회한다.
    Optional<PaymentCancellation> findPaymentCancellationById(
            @Param("cancellationId") long cancellationId
    );

    // 결제 승인 뒤 내부 처리 실패를 복구하는 시스템 취소 요청을 멱등키로 조회한다.
    Optional<PaymentCancellation> findPaymentCancellationByIdempotencyKey(
            @Param("idempotencyKey") String idempotencyKey
    );

    // 결제에 이미 진행 중인 취소 요청이 있으면 재시도에서 같은 멱등 키를 재사용한다.
    Optional<PaymentCancellation> findRequestedCancellationByPaymentId(
            @Param("paymentId") long paymentId
    );

    // READY 또는 DONE 결제에 시스템 보상 취소 요청을 한 건만 생성한다.
    int insertCompensationCancellation(PaymentCancellation cancellation);

    // 보상 재처리에 필요한 paymentKey를 READY 또는 DONE 결제에 한 번만 연결한다.
    int attachRecoveryPaymentKey(
            @Param("paymentId") long paymentId,
            @Param("paymentKey") String paymentKey
    );

    // 미승인 보상 요청을 해제할 때 READY 결제에 임시로 연결했던 paymentKey를 제거한다.
    int clearRecoveryPaymentKey(
            @Param("paymentId") long paymentId,
            @Param("paymentKey") String paymentKey
    );

    // 아직 끝나지 않은 시스템 보상 취소 요청을 오래된 순서로 조회한다.
    List<PaymentCancellation> findRequestedCompensations(
            @Param("limit") int limit
    );

    // REQUESTED 환불과 부모 DONE 결제를 함께 완료·취소 처리한다.
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

    // 시스템 보상 취소 결과를 기록하고 READY 또는 DONE 결제를 CANCELED로 맞춘다.
    int completeCompensationIfRequested(
            @Param("cancellationId") long cancellationId,
            @Param("paymentKey") String paymentKey,
            @Param("transactionKey") String transactionKey,
            @Param("canceledAt") LocalDateTime canceledAt
    );
}
