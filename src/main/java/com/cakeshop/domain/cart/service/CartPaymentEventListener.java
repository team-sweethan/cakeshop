package com.cakeshop.domain.cart.service;

import com.cakeshop.domain.payment.event.GeneralPaymentCompletedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** 결제 커밋이 완료된 뒤에만 장바구니 정리를 시작해 결제 실패 시 장바구니가 사라지지 않게 한다. */
@Component
@RequiredArgsConstructor
@Slf4j
public class CartPaymentEventListener {

    private final CartPaymentCommandService cartPaymentCommandService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleGeneralPaymentCompleted(GeneralPaymentCompletedEvent event) {
        try {
            cartPaymentCommandService.removeItemsAfterPayment(event.orderId());
        } catch (RuntimeException exception) {
            // 결제는 이미 커밋됐으므로 장바구니 정리 실패가 고객 결제 결과를 바꾸지 않게 한다.
            log.warn("Cart cleanup after completed payment failed. orderId={}", event.orderId());
        }
    }
}
