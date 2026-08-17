package com.cakeshop.domain.payment.service;

import com.cakeshop.domain.payment.infra.TossPaymentClient;
import com.cakeshop.domain.payment.infra.TossPaymentClient.CancellationResult;
import com.cakeshop.domain.payment.infra.TossPaymentClient.PaymentLookupResult;

import java.util.Optional;

/** Toss 조회 결과에서 완료된 전체 취소 결과만 추린다. */
final class TossCancellationResultResolver {

    private static final String TOSS_CANCELED = "CANCELED";

    private TossCancellationResultResolver() {
    }

    static Optional<CancellationResult> findCompleted(
            TossPaymentClient tossPaymentClient,
            String paymentKey
    ) {
        return tossPaymentClient.find(paymentKey)
                .flatMap(TossCancellationResultResolver::findCompleted);
    }

    static Optional<CancellationResult> findCompleted(PaymentLookupResult lookup) {
        return Optional.ofNullable(lookup)
                .filter(payment -> TOSS_CANCELED.equals(payment.status()))
                .map(PaymentLookupResult::cancellation)
                .filter(TossCancellationResultResolver::isCompleted);
    }

    private static boolean isCompleted(CancellationResult result) {
        return result != null
                && TOSS_CANCELED.equals(result.status())
                && result.transactionKey() != null
                && !result.transactionKey().isBlank()
                && result.canceledAt() != null;
    }
}
