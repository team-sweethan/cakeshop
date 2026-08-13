package com.cakeshop.domain.chat.service;

import com.cakeshop.domain.chat.entity.ChatRoom;
import com.cakeshop.domain.chat.mapper.ChatMapper;
import com.cakeshop.domain.order.dto.view.OrderChatView;
import com.cakeshop.domain.order.service.OrderChatQueryService;
import com.cakeshop.domain.payment.event.GeneralPaymentCompletedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class ChatEventListener {

    private final ChatMapper chatMapper;
    private final OrderChatQueryService orderChatQueryService;
    private final SimpMessagingTemplate messagingTemplate;

    @EventListener
    public void handlePaymentCompleted(GeneralPaymentCompletedEvent event) {
        long orderId = event.orderId();

        // 1. 주문 연동 전용 서비스(OrderChatQueryService)로 주문 정보 안전 조회
        OrderChatView orderView = orderChatQueryService.findOrder(orderId);
        if (orderView == null || orderView.memberId() == null) {
            return;
        }

        // 2. 고객의 1:1 채팅방 존재 여부 확인
        ChatRoom room = chatMapper.findChatRoomByCustomerId(orderView.memberId());
        if (room == null) {
            return;
        }

        // 3. 해당 고객의 최신 주문 목록 전체를 연동 전용 서비스로 조회
        List<OrderChatView> orders = orderChatQueryService.findOrdersByCustomerId(orderView.memberId());

        // 4. 해당 1:1 채팅방 고유 웹소켓 토픽으로 실시간 주문 배너 갱신 방송 (/topic/chat/{roomId}/orders)
        messagingTemplate.convertAndSend(
                "/topic/chat/" + room.getId() + "/orders",
                orders
        );
    }
}
