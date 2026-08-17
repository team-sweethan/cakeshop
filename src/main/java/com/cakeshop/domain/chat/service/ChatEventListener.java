package com.cakeshop.domain.chat.service;

import com.cakeshop.domain.chat.dto.view.ChatRoomOrderResponse;
import com.cakeshop.domain.order.dto.view.OrderChatView;
import com.cakeshop.domain.order.service.OrderChatQueryService;
import com.cakeshop.domain.payment.event.CustomPaymentChatCompletedEvent;
import com.cakeshop.domain.payment.event.GeneralPaymentCompletedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChatEventListener {

    private final ChatService chatService;
    private final OrderChatQueryService orderChatQueryService;
    private final SimpMessagingTemplate messagingTemplate;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleGeneralPaymentCompleted(GeneralPaymentCompletedEvent event) {
        broadcastOrderUpdate(event.orderId());
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleCustomPaymentCompleted(CustomPaymentChatCompletedEvent event) {
        broadcastOrderUpdate(event.orderId());
    }

    private void broadcastOrderUpdate(long orderId) {
        try {
            OrderChatView orderView = orderChatQueryService.findOrder(orderId);
            if (orderView == null || orderView.memberId() == null) {
                return;
            }

            Long roomId = chatService.findChatRoomIdByCustomerId(orderView.memberId());
            if (roomId == null) {
                return;
            }

            List<ChatRoomOrderResponse> orders = chatService.getChatRoomOrders(
                    roomId,
                    orderView.memberId(),
                    false
            );

            messagingTemplate.convertAndSend("/topic/chat/" + roomId + "/orders", orders);
        } catch (Exception e) {
            log.error("채팅방 결제 완료 이벤트 실시간 전파 처리 실패 (orderId={})", orderId);
        }
    }
}
