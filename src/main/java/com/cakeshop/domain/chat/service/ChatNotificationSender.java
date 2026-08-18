package com.cakeshop.domain.chat.service;

import com.cakeshop.domain.member.service.MemberChatQueryService;
import com.cakeshop.domain.notification.dto.form.NotificationRequest;
import com.cakeshop.domain.notification.entity.NotificationType;
import com.cakeshop.domain.notification.service.NotificationService;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

/**
 * 채팅 알림 발송 서비스
 * - 고객이 메시지 전송 시: 활성 관리자들에게 ADMIN_CHAT 알림 발송
 * - 관리자가 메시지 전송 시: 고객에게 CUSTOMER_CHAT 알림 발송
 *
 * ⚠️ 반드시 트랜잭션 완료(커밋) 이후에 호출해야 합니다.
 *    ChatStompController / ChatApiController 에서 chatService.sendMessage() 리턴 후 호출하세요.
 *    ChatService.sendMessage() 내부에서 호출하면 이중 afterCommit 중첩으로 WebSocket 전송이 묵살됩니다.
 */
@Slf4j
@Service
public class ChatNotificationSender {

    private final NotificationService notificationService;
    private final MemberChatQueryService memberChatQueryService;

    public ChatNotificationSender(@Lazy NotificationService notificationService,
                                  MemberChatQueryService memberChatQueryService) {
        this.notificationService = notificationService;
        this.memberChatQueryService = memberChatQueryService;
    }

    /**
     * 고객이 메시지를 보냈을 때 관리자들에게 알림 발송
     * (트랜잭션 외부에서 호출할 것)
     */
    public void sendAdminChatNotification(Long chatRoomId, Long messageId, Long customerId, String customerName) {
        try {
            List<Long> activeAdminIds = memberChatQueryService.findActiveAdminIds();
            if (activeAdminIds == null || activeAdminIds.isEmpty()) return;

            String cName = (customerName != null && !customerName.isBlank()) ? customerName : "고객";

            for (Long adminId : activeAdminIds) {
                try {
                    notificationService.makeNotification(NotificationRequest.builder()
                            .receiverId(adminId)
                            .actorId(customerId)
                            .chatRoomId(chatRoomId)
                            .chatMessageId(messageId)
                            .type(NotificationType.ADMIN_CHAT)
                            .eventKey("ADMIN_CHAT:" + adminId + ":ROOM_" + chatRoomId)
                            .args(new Object[]{cName})
                            .build());
                } catch (Exception e) {
                    log.error("관리자(id={}) 채팅 알림 발송 중 개별 오류 발생: roomId={}", adminId, chatRoomId, e);
                }
            }
        } catch (Exception e) {
            log.error("관리자 채팅 알림 발송 중 오류 발생: roomId={}", chatRoomId, e);
        }
    }

    /**
     * 관리자가 답변 메시지를 보냈을 때 고객에게 알림 발송
     * (트랜잭션 외부에서 호출할 것)
     */
    public void sendCustomerChatNotification(Long chatRoomId, Long messageId, Long customerId, Long adminId) {
        try {
            if (customerId == null || customerId <= 0) return;

            notificationService.makeNotification(NotificationRequest.builder()
                    .receiverId(customerId)
                    .actorId(adminId)
                    .chatRoomId(chatRoomId)
                    .chatMessageId(messageId)
                    .type(NotificationType.CUSTOMER_CHAT)
                    .eventKey("CUSTOMER_CHAT:" + customerId + ":ROOM_" + chatRoomId)
                    .args(new Object[0])
                    .build());
        } catch (Exception e) {
            log.error("고객 채팅 알림 발송 중 오류 발생: roomId={}", chatRoomId, e);
        }
    }
}
