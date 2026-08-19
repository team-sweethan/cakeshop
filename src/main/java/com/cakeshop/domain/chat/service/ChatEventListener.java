package com.cakeshop.domain.chat.service;

import com.cakeshop.domain.chat.dto.view.ChatRoomOrderResponse;
import com.cakeshop.domain.notification.event.CustomOrderRejectedChatEvent;
import com.cakeshop.domain.order.dto.view.OrderChatView;
import com.cakeshop.domain.order.service.OrderChatQueryService;
import com.cakeshop.domain.payment.event.CustomPaymentChatCompletedEvent;
import com.cakeshop.domain.payment.event.GeneralPaymentCompletedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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

    /** 반려 사유 채팅 자동 발송 시 사용하는 시스템 발신자 회원 ID (기본값: 1) */
    @Value("${chat.system-sender-id:1}")
    private Long systemSenderId;

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

    /**
     * 주문제작 반려 알림 커밋 완료 후 반려 사유를 고객 채팅방에 시스템 메시지로 자동 발송한다.
     * - 채팅방이 없으면 getOrMakeChatRoom()으로 자동 생성한다.
     * - rejectReason이 없는 경우(0원 반려 등) 발송을 스킵한다.
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleCustomOrderRejected(CustomOrderRejectedChatEvent event) {
        try {
            OrderChatView order = orderChatQueryService.findOrder(event.orderId());
            if (order == null || order.rejectReason() == null || order.rejectReason().isBlank()) {
                log.debug("주문제작 반려 채팅 발송 스킵: 반려 사유 없음 (orderId={})", event.orderId());
                return;
            }
            chatService.sendSystemRejectionMessage(event.customerId(), order.rejectReason(), systemSenderId);
            log.info("주문제작 반려 사유 채팅 메시지 발송 완료 (orderId={}, customerId={})", event.orderId(), event.customerId());
        } catch (Exception e) {
            log.error("주문제작 반려 사유 채팅 메시지 발송 실패 (orderId={}, customerId={})",
                    event.orderId(), event.customerId(), e);
        }
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
