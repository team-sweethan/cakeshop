package com.cakeshop.domain.payment.service;

import com.cakeshop.domain.payment.infra.TossPaymentClient;
import com.cakeshop.domain.payment.infra.TossPaymentClient.CancellationResult;
import com.cakeshop.domain.payment.service.RefundService.RefundRequest;
import com.cakeshop.global.error.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** 외부 Toss 취소와 내부 환불 완료 트랜잭션을 조정한다. */
@Service
@RequiredArgsConstructor
public class RefundFacade {

    private final RefundService refundService;
    private final TossPaymentClient tossPaymentClient;

    public void cancelCustomerOrder(long memberId, long orderId, String reason) {
        RefundRequest request = refundService.prepareCustomerCancellation(
                memberId,
                orderId,
                reason
        );
        cancel(request);
    }

    public void cancelAdminOrder(long adminMemberId, long orderId, String reason) {
        RefundRequest request = refundService.prepareAdminCancellation(
                adminMemberId,
                orderId,
                reason
        );
        cancel(request);
    }

    private void cancel(RefundRequest request) {
        CancellationResult result;
        try {
            result = tossPaymentClient.cancel(
                    request.paymentKey(),
                    request.reason(),
                    request.idempotencyKey()
            );
        } catch (BusinessException exception) {
            refundService.failRequestedCancellation(request.cancellationId());
            throw exception;
        }
        // PG 성공 뒤 내부 확정이 실패하면 REQUESTED를 유지해 같은 멱등 키로 재처리할 수 있다.
        refundService.completeCancellation(request, result);
    }
}
