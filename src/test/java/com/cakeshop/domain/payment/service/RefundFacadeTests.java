package com.cakeshop.domain.payment.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cakeshop.domain.order.entity.OrderStatus;
import com.cakeshop.domain.payment.error.PaymentErrorCode;
import com.cakeshop.domain.payment.infra.TossPaymentClient;
import com.cakeshop.domain.payment.infra.TossPaymentClient.CancellationResult;
import com.cakeshop.domain.payment.service.RefundService.RefundRequest;
import com.cakeshop.global.error.BusinessException;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RefundFacadeTests {

    @Mock
    private RefundService refundService;

    @Mock
    private TossPaymentClient tossPaymentClient;

    @InjectMocks
    private RefundFacade refundFacade;

    @Test
    void cancelCustomerOrder_tossSuccess_completesInternalCancellation() {
        RefundRequest request = request();
        CancellationResult result = new CancellationResult(
                "CANCELED", "transaction-key", request.requestedAt().plusSeconds(1));
        when(refundService.prepareCustomerCancellation(3L, 10L, "단순 변심")).thenReturn(request);
        when(tossPaymentClient.cancel("payment-key", "단순 변심", "idempotency-key"))
                .thenReturn(result);

        refundFacade.cancelCustomerOrder(3L, 10L, "단순 변심");

        verify(refundService).completeCancellation(request, result);
    }

    @Test
    void cancelCustomerOrder_tossFailure_marksRequestFailed() {
        RefundRequest request = request();
        when(refundService.prepareCustomerCancellation(3L, 10L, "단순 변심")).thenReturn(request);
        when(tossPaymentClient.cancel("payment-key", "단순 변심", "idempotency-key"))
                .thenThrow(new BusinessException(PaymentErrorCode.TOSS_CANCEL_FAILED));

        assertThatThrownBy(() -> refundFacade.cancelCustomerOrder(3L, 10L, "단순 변심"))
                .isInstanceOf(BusinessException.class);

        verify(refundService).failRequestedCancellation(30L);
    }

    @Test
    void cancelAdminOrder_tossSuccess_completesWithAdminRequest() {
        RefundRequest request = request("ADMIN", "매장 사정");
        CancellationResult result = new CancellationResult(
                "CANCELED", "transaction-key", request.requestedAt().plusSeconds(1));
        when(refundService.prepareAdminCancellation(7L, 10L, "매장 사정"))
                .thenReturn(request);
        when(tossPaymentClient.cancel("payment-key", "매장 사정", "idempotency-key"))
                .thenReturn(result);

        refundFacade.cancelAdminOrder(7L, 10L, "매장 사정");

        verify(refundService).completeCancellation(request, result);
    }

    private RefundRequest request() {
        return request("CUSTOMER", "단순 변심");
    }

    private RefundRequest request(String canceledBy, String reason) {
        return new RefundRequest(
                30L,
                10L,
                OrderStatus.READY_FOR_PICKUP,
                "payment-key",
                "idempotency-key",
                reason,
                canceledBy,
                LocalDateTime.of(2026, 8, 2, 15, 0)
        );
    }
}
