package com.cakeshop.domain.payment.service;

import com.cakeshop.domain.order.dto.view.OrderDetailView;
import com.cakeshop.domain.order.entity.OrderStatus;
import com.cakeshop.domain.order.entity.OrderType;
import com.cakeshop.domain.order.service.customer.CustomerOrderQueryService;
import com.cakeshop.domain.member.service.MemberService;
import com.cakeshop.domain.payment.dto.form.TossPaymentSuccessForm;
import com.cakeshop.domain.payment.dto.view.PaymentCheckoutView;
import com.cakeshop.domain.payment.dto.view.PaymentCompletionView;
import com.cakeshop.domain.payment.dto.view.PaymentFailureView;
import com.cakeshop.domain.payment.entity.Payment;
import com.cakeshop.domain.payment.error.PaymentErrorCode;
import com.cakeshop.domain.store.service.StoreService;
import com.cakeshop.global.error.BusinessException;
import com.cakeshop.global.error.CommonErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

/** 회원 소유권과 결제 상태를 검증해 결제 화면용 조회 결과를 구성한다. */
@Service
public class PaymentQueryService {

    private static final int TOSS_ORDER_NAME_MAX_LENGTH = 100;

    private final CustomerOrderQueryService orderQueryService;
    private final MemberService memberService;
    private final PaymentService paymentService;
    private final StoreService storeService;
    private final Clock clock;
    private final String clientKey;
    private final String secretKey;

    public PaymentQueryService(
            CustomerOrderQueryService orderQueryService,
            MemberService memberService,
            PaymentService paymentService,
            StoreService storeService,
            Clock clock,
            @Value("${app.payment.toss.client-key:}") String clientKey,
            @Value("${app.payment.toss.secret-key:}") String secretKey
    ) {
        this.orderQueryService = orderQueryService;
        this.memberService = memberService;
        this.paymentService = paymentService;
        this.storeService = storeService;
        this.clock = clock;
        this.clientKey = clientKey;
        this.secretKey = secretKey;
    }

    /** 결제 기한 및 정보 검증 -> 일반 주믄용 Toss 결제 화면 데이터 구성**/
    @Transactional(readOnly = true)
    public PaymentCheckoutView getCheckout(long memberId, String memberEmail, long orderId) {
        if (!memberService.isActiveMember(memberId)) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN);
        }

        OrderDetailView order = orderQueryService.getMemberOrder(memberId, orderId);
        // 일반 상품 검증.
        validatePendingGeneralOrder(order);
        // 결제 기한 검증
        validatePaymentExpiration(order.paymentExpiresAt());

        Payment payment = paymentService.getReadyPayment(orderId);
        // 결제 정보 검증
        validateReadyPayment(order, payment);

        List<PaymentCheckoutView.ItemView> items = order.items().stream()
                .map(item -> new PaymentCheckoutView.ItemView(
                        item.productName(),
                        item.quantity(),
                        item.totalAmount(),
                        item.options().stream()
                                .map(option -> new PaymentCheckoutView.OptionView(
                                        option.groupName(),
                                        option.optionName()
                                ))
                                .toList()
                ))
                .toList();

        return new PaymentCheckoutView(
                order.orderId(),
                order.orderNumber(),
                payment.getTossOrderId(),
                createOrderName(order),
                order.originalAmount(),
                order.discountAmount(),
                order.finalAmount(),
                order.pickupAt(),
                order.paymentExpiresAt(),
                order.ordererName(),
                memberEmail,
                order.ordererPhone(),
                clientKey,
                hasText(clientKey) && hasText(secretKey),
                items
        );
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    /**
     * Toss 성공 리다이렉트 요청이 회원 소유의 주문·결제 정보와 일치하는지 검증한다.
     * 완료 결제는 동일 요청의 재시도 여부를, 미완료 결제는 승인 전 조건을 확인한다.
     */
    @Transactional(readOnly = true)
    public void validateSuccessCallback(long memberId, long orderId, TossPaymentSuccessForm form) {

        // 회원 소유 주문을 조회한다.
        OrderDetailView order = orderQueryService.getMemberOrder(memberId, orderId);
        // 일반 상품 주문인지 확인한다.
        validateGeneralOrder(order);
        // 성공 리다이렉트로 전달된 주문 ID와 금액이 저장된 주문 정보와 일치하는지 확인한다.
        validateSuccessRedirectOrder(order, form);

        // 이미 완료된 결제는 재승인하지 않고, 동일 결제 요청인지 확인한다.
        Payment completedPayment = paymentService.findDonePayment(orderId)
                .orElse(null);
        if (completedPayment != null) {
            // Toss 주문 ID, 금액, 결제 키가 완료된 결제 정보와 일치하는지 확인한다.
            validateCompletedPayment(completedPayment, form);
            return;
        }

        // 완료되지 않은 주문은 결제 대기 상태여야 한다.
        validatePendingGeneralOrder(order);
        // 결제 기한이 만료되지 않았는지 확인한다.
        validatePaymentExpiration(order.paymentExpiresAt());

        // READY 결제의 Toss 주문 ID와 금액이 주문 정보와 일치하는지 확인한다.
        validateReadyPayment(order, paymentService.getReadyPayment(orderId));
    }

    /** 결제 실패 안내와 재결제 가능 여부 조회.**/
    @Transactional(readOnly = true)
    public PaymentFailureView getFailure(long memberId, long orderId, String failureCode) {

        OrderDetailView order = orderQueryService.getMemberOrder(memberId, orderId);
        validateGeneralOrder(order);

        boolean retryAvailable = order.status() == OrderStatus.PENDING_PAYMENT
                && order.paymentExpiresAt() != null
                && LocalDateTime.now(clock).isBefore(order.paymentExpiresAt());

        return new PaymentFailureView(
                orderId,
                failureMessage(failureCode),
                retryAvailable
        );
    }

    @Transactional(readOnly = true)
    public PaymentCompletionView getCompletion(long memberId, long orderId) {
        OrderDetailView order = orderQueryService.getMemberOrder(memberId, orderId);
        validateGeneralOrder(order);
        Payment payment = paymentService.getDonePayment(orderId);

        if (!sameAmount(order.finalAmount(), payment.getAmount())) {
            throw new BusinessException(PaymentErrorCode.AMOUNT_MISMATCH);
        }

        return new PaymentCompletionView(
                order.orderId(),
                order.orderNumber(),
                payment.getAmount(),
                payment.getMethod(),
                order.pickupAt(),
                storeService.getStoreView().pickupPlace(),
                order.items().stream()
                        .map(item -> new PaymentCompletionView.ItemView(
                                item.productName(),
                                item.quantity()
                        ))
                        .toList()
        );
    }

    /** OrderType = General**/
    private void validateGeneralOrder(OrderDetailView order) {
        if (order.orderType() != OrderType.GENERAL) {
            throw new BusinessException(PaymentErrorCode.READY_PAYMENT_NOT_FOUND);
        }
    }

    /** OrderStatus = PENDING_PAYMENT(결제 대기)**/
    private void validatePendingGeneralOrder(OrderDetailView order) {
        validateGeneralOrder(order);
        if (order.status() != OrderStatus.PENDING_PAYMENT) {
            throw new BusinessException(PaymentErrorCode.READY_PAYMENT_NOT_FOUND);
        }
    }

    /** 결제 만료 시각이 없거나, 이미 지났으면 결제 차단.**/
    private void validatePaymentExpiration(LocalDateTime paymentExpiresAt) {
        if (paymentExpiresAt == null || !LocalDateTime.now(clock).isBefore(paymentExpiresAt)) {
            throw new BusinessException(PaymentErrorCode.PAYMENT_EXPIRED);
        }
    }
    /** 결제 대기 상태 결제 정보 == 주문 번호 & 최종금액**/
    private void validateReadyPayment(OrderDetailView order, Payment payment) {
        if (payment.getTossOrderId() == null || !payment.getTossOrderId().equals(order.orderNumber())) {
            throw new BusinessException(PaymentErrorCode.TOSS_ORDER_ID_MISMATCH);
        }
        if (!sameAmount(order.finalAmount(), payment.getAmount())) {
            throw new BusinessException(PaymentErrorCode.AMOUNT_MISMATCH);
        }
    }

    /** Toss 성공 리다이렉트의 주문 ID와 들어온 주문 정보와 일치하는지 검증**/
    private void validateSuccessRedirectOrder(OrderDetailView order, TossPaymentSuccessForm form) {

        if (form == null || !order.orderNumber().equals(form.getOrderId())) {
            throw new BusinessException(PaymentErrorCode.TOSS_ORDER_ID_MISMATCH);
        }
        if (!sameAmount(order.finalAmount(), form.getAmount())) {
            throw new BusinessException(PaymentErrorCode.AMOUNT_MISMATCH);
        }
    }

    private void validateCompletedPayment(
            Payment payment,
            TossPaymentSuccessForm form
    ) {
        if (!payment.getTossOrderId().equals(form.getOrderId())) {
            throw new BusinessException(PaymentErrorCode.TOSS_ORDER_ID_MISMATCH);
        }
        if (!sameAmount(payment.getAmount(), form.getAmount())) {
            throw new BusinessException(PaymentErrorCode.AMOUNT_MISMATCH);
        }
        if (payment.getPaymentKey() == null
                || !payment.getPaymentKey().equals(form.getPaymentKey())) {
            throw new BusinessException(PaymentErrorCode.TOSS_APPROVAL_FAILED);
        }
    }

    private String createOrderName(OrderDetailView order) {
        if (order.items().isEmpty()) {
            throw new BusinessException(CommonErrorCode.INTERNAL_ERROR);
        }
        String firstProductName = order.items().getFirst().productName();
        String orderName = order.items().size() == 1
                ? firstProductName
                : firstProductName + " 외 " + (order.items().size() - 1) + "건";
        if (orderName.length() <= TOSS_ORDER_NAME_MAX_LENGTH) {
            return orderName;
        }
        return orderName.substring(0, TOSS_ORDER_NAME_MAX_LENGTH);
    }

    private String failureMessage(String failureCode) {
        if ("PAY_PROCESS_CANCELED".equals(failureCode)) {
            return "결제가 취소되었습니다. 결제 제한 시간 안에 다시 시도할 수 있습니다.";
        }
        if ("REJECT_CARD_COMPANY".equals(failureCode)) {
            return "카드사에서 결제를 승인하지 않았습니다. 다른 결제수단을 이용해 주세요.";
        }
        return "결제를 완료하지 못했습니다. 잠시 후 다시 시도해 주세요.";
    }

    private boolean sameAmount(BigDecimal expected, BigDecimal actual) {
        return expected != null
                && actual != null
                && expected.compareTo(actual) == 0;
    }
}
