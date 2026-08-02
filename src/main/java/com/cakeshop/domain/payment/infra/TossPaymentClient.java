package com.cakeshop.domain.payment.infra;

import com.cakeshop.domain.payment.error.PaymentErrorCode;
import com.cakeshop.global.error.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.List;

// 토스 승인·취소·조회 API 클라이언트 — 작업별 UUID를 Idempotency-Key로 사용
@Component
public class TossPaymentClient {

    private static final String IDEMPOTENCY_KEY = "Idempotency-Key";

    private final RestClient restClient;
    private final String authorization;

    public TossPaymentClient(
            @Value("${app.payment.toss.base-url}") String baseUrl,
            @Value("${app.payment.toss.secret-key:}") String secretKey
    ) {
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
        this.authorization = createAuthorization(secretKey);
    }

    /** Toss 결제 승인 API를 호출하고 내부 완료 처리에 필요한 결과만 반환한다. */
    public ApprovalResult approve(
            String paymentKey,
            String orderId,
            long amount,
            String idempotencyKey
    ) {
        if (authorization == null) {
            throw new BusinessException(
                    PaymentErrorCode.TOSS_APPROVAL_FAILED
            );
        }

        try {
            TossPaymentResponse response = restClient.post()
                    .uri("/v1/payments/confirm")
                    .header(HttpHeaders.AUTHORIZATION, authorization)
                    .header(IDEMPOTENCY_KEY, idempotencyKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new TossApprovalRequest(
                            paymentKey,
                            orderId,
                            amount
                    ))
                    .retrieve()
                    .body(TossPaymentResponse.class);

            if (response == null || response.approvedAt() == null) {
                throw new BusinessException(
                        PaymentErrorCode.TOSS_APPROVAL_FAILED
                );
            }

            return new ApprovalResult(
                    response.paymentKey(),
                    response.orderId(),
                    response.method(),
                    response.status(),
                    response.totalAmount(),
                    response.approvedAt().toLocalDateTime()
            );
        } catch (RestClientException exception) {
            // PG 오류 응답의 상세 메시지나 인증 정보를 사용자에게 노출하지 않는다.
            throw new BusinessException(
                    PaymentErrorCode.TOSS_APPROVAL_FAILED
            );
        }
    }

    /** Toss 결제 전체 취소 API를 호출하고 내부 완료 처리에 필요한 결과를 반환한다. */
    public CancellationResult cancel(String paymentKey, String reason, String idempotencyKey) {
        if (authorization == null
                || paymentKey == null
                || paymentKey.isBlank()
                || reason == null
                || reason.isBlank()
                || idempotencyKey == null
                || idempotencyKey.isBlank()) {
            throw new BusinessException(PaymentErrorCode.TOSS_CANCEL_FAILED);
        }

        try {
            TossPaymentResponse response = restClient.post()
                    .uri("/v1/payments/{paymentKey}/cancel", paymentKey)
                    .header(HttpHeaders.AUTHORIZATION, authorization)
                    .header(IDEMPOTENCY_KEY, idempotencyKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new TossCancelRequest(reason))
                    .retrieve()
                    .body(TossPaymentResponse.class);

            if (response == null
                    || !"CANCELED".equals(response.status())
                    || response.cancels() == null
                    || response.cancels().isEmpty()) {
                throw new BusinessException(PaymentErrorCode.TOSS_CANCEL_FAILED);
            }
            TossCancellationResponse cancellation = response.cancels().getLast();
            if (cancellation.transactionKey() == null
                    || cancellation.transactionKey().isBlank()
                    || cancellation.canceledAt() == null) {
                throw new BusinessException(PaymentErrorCode.TOSS_CANCEL_FAILED);
            }
            return new CancellationResult(
                    response.status(),
                    cancellation.transactionKey(),
                    cancellation.canceledAt().toLocalDateTime()
            );
        } catch (RestClientException exception) {
            throw new BusinessException(PaymentErrorCode.TOSS_CANCEL_FAILED);
        }
    }

    public void find(String paymentKey) {
        // TODO: 상태 대조용 결제 조회
    }

    private String createAuthorization(String secretKey) {
        if (secretKey == null || secretKey.isBlank()) {
            return null;
        }

        String credentials = Base64.getEncoder().encodeToString(
                (secretKey + ":").getBytes(StandardCharsets.UTF_8)
        );
        return "Basic " + credentials;
    }

    private record TossApprovalRequest(
            String paymentKey,
            String orderId,
            long amount
    ) {
    }

    private record TossCancelRequest(String cancelReason) {
    }

    private record TossPaymentResponse(
            String paymentKey,
            String orderId,
            String method,
            String status,
            long totalAmount,
            OffsetDateTime approvedAt,
            List<TossCancellationResponse> cancels
    ) {
    }

    private record TossCancellationResponse(
            String transactionKey,
            OffsetDateTime canceledAt
    ) {
    }

    public record ApprovalResult(
            String paymentKey,
            String orderId,
            String method,
            String status,
            long totalAmount,
            LocalDateTime approvedAt
    ) {
    }

    public record CancellationResult(
            String status,
            String transactionKey,
            LocalDateTime canceledAt
    ) {
    }
}
