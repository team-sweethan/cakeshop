package com.cakeshop.domain.chat.service;

import com.cakeshop.domain.chat.dto.view.ChatRoomOrderResponse;
import com.cakeshop.domain.chat.entity.ChatRoom;
import com.cakeshop.domain.chat.mapper.ChatMapper;
import com.cakeshop.domain.order.dto.view.OrderChatView;
import com.cakeshop.domain.order.service.OrderChatQueryService;
import com.cakeshop.domain.payment.event.GeneralPaymentCompletedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChatEventListener {

    private final ChatMapper chatMapper;
    private final OrderChatQueryService orderChatQueryService;
    private final ChatService chatService;
    private final SimpMessagingTemplate messagingTemplate;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handlePaymentCompleted(GeneralPaymentCompletedEvent event) {
        try {
            long orderId = event.orderId();

            // 1. 주문 연동 전용 서비스(OrderChatQueryService)로 주문 정보 조회
            OrderChatView orderView = orderChatQueryService.findOrder(orderId);
            if (orderView == null || orderView.memberId() == null) {
                return;
            }

            // 2. 고객의 1:1 채팅방 존재 여부 확인
            ChatRoom room = chatMapper.findChatRoomByCustomerId(orderView.memberId());
            if (room == null) {
                return;
            }

            // 3. 화면 스펙(ChatRoomOrderResponse)에 맞는 연동 주문 배너 DTO 목록 조회
            List<ChatRoomOrderResponse> orders = chatService.getChatRoomOrders(
                    room.getId(),
                    orderView.memberId(),
                    false
            );

            // 4. 해당 1:1 채팅방 고유 웹소켓 토픽으로 실시간 주문 배너 갱신 방송 (/topic/chat/{roomId}/orders)
            messagingTemplate.convertAndSend(
                    "/topic/chat/" + room.getId() + "/orders",
                    orders
            );
        } catch (Exception e) {
            log.error("채팅방 결제 완료 이벤트 실시간 전파 처리 실패 (orderId={})", event.orderId());
        }
    }
}
