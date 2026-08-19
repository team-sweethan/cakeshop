package com.cakeshop.domain.chat.service;

import com.cakeshop.domain.chat.dto.view.ChatMessageResponse;
import com.cakeshop.domain.chat.dto.view.ChatRoomListResponse;
import com.cakeshop.domain.chat.dto.view.ChatRoomOrderResponse;
import com.cakeshop.domain.notification.event.CustomOrderRejectedChatEvent;
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

    /**
     * 주문제작 반려 알림 커밋 완료 후 반려 사유를 고객 채팅방에 시스템 메시지로 자동 발송한다.
     * - 채팅방이 없으면 getOrMakeChatRoom()으로 자동 생성한다.
     * - rejectReason이 없는 경우(0원 반려 등) 발송을 스킵한다.
     * - 발송 성공 후 /topic/chat/{roomId}에 브로드캐스트하여 고객 화면 실시간 갱신을 보장한다.
     * [P3 알림] 비동기 실패 시 재처리 없음 — 이 구현은 일시적 오류에 의한 누락 가능성을 허용한다.
     *           내구성이 필요하면 별도 outbox 이슈로 처리한다.
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

            // P1 수정: sendSystemRejectionMessage 내부에서 활성 관리자 동적 조회
            ChatMessageResponse response = chatService.sendSystemRejectionMessage(
                    event.customerId(), order.rejectReason());
            if (response == null) return;

            // P2 수정: 저장된 메시지를 채팅 토픽에 브로드캐스트하여 고객 화면 실시간 갱신
            messagingTemplate.convertAndSend("/topic/chat/" + response.getChatRoomId(), response);

            // P2 수정: 고객 채팅 화면 연동 주문 목록 사이드바 실시간 갱신 (UNDER_REVIEW -> REJECTED 반영)
            broadcastOrderUpdate(event.orderId());

            // 관리자 방 목록 갱신 및 최상단 정렬 전파 (chat-admin.js의 isNewMessageEvent 조건인 content, senderType 포함)
            ChatRoomListResponse roomResponse = chatService.getAdminChatRoomResponse(response.getChatRoomId());
            if (roomResponse != null) {
                java.util.Map<String, Object> adminPayload = new java.util.HashMap<>();
                adminPayload.put("chatRoomId", roomResponse.getChatRoomId());
                adminPayload.put("customerId", roomResponse.getCustomerId());
                adminPayload.put("customerName", roomResponse.getCustomerName() != null ? roomResponse.getCustomerName() : "고객");
                adminPayload.put("responseStatus", roomResponse.getResponseStatus());
                adminPayload.put("lastMessageContent", roomResponse.getLastMessageContent());
                adminPayload.put("lastMessageCreatedAt", roomResponse.getLastMessageCreatedAt());
                adminPayload.put("lastMessageId", roomResponse.getLastMessageId());
                adminPayload.put("unreadCount", roomResponse.getUnreadCount());
                adminPayload.put("content", roomResponse.getLastMessageContent());
                adminPayload.put("senderType", "ADMIN");

                messagingTemplate.convertAndSend("/topic/admin/rooms", (Object) adminPayload);
            }

            log.info("주문제작 반려 사유 채팅 메시지 발송 완료 (orderId={}, customerId={}, roomId={})",
                    event.orderId(), event.customerId(), response.getChatRoomId());
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
