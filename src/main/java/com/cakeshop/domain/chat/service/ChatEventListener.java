package com.cakeshop.domain.chat.service;

import com.cakeshop.domain.payment.event.CustomPaymentChatCompletedEvent;
import com.cakeshop.domain.payment.event.GeneralPaymentCompletedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChatEventListener {

    private final ChatService chatService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleGeneralPaymentCompleted(GeneralPaymentCompletedEvent event) {
        try {
            chatService.broadcastOrderUpdateForPayment(event.orderId());
        } catch (Exception e) {
            log.error("채팅방 일반 결제 완료 이벤트 실시간 전파 처리 실패 (orderId={})", event.orderId());
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleCustomPaymentCompleted(CustomPaymentChatCompletedEvent event) {
        try {
            chatService.broadcastOrderUpdateForPayment(event.orderId());
        } catch (Exception e) {
            log.error("채팅방 주문제작 결제 완료 이벤트 실시간 전파 처리 실패 (orderId={})", event.orderId());
        }
    }
}
