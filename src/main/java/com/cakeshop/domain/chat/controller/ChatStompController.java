package com.cakeshop.domain.chat.controller;

import com.cakeshop.domain.chat.dto.form.ChatMessageSendRequest;
import com.cakeshop.domain.chat.dto.form.ChatRoomReadRequest;
import com.cakeshop.domain.chat.dto.view.ChatMessageResponse;
import com.cakeshop.domain.chat.service.ChatService;
import com.cakeshop.global.error.BusinessException;
import com.cakeshop.global.error.CommonErrorCode;
import com.cakeshop.global.security.MemberDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.Map;

@Controller
@RequiredArgsConstructor
public class ChatStompController {

    private final ChatService chatService;
    private final SimpMessagingTemplate messagingTemplate;

    // 1. 실시간 메시지 전송 (@MessageMapping("/chat/message"))
    // 클라이언트 발신: /app/chat/message
    // 브로드캐스트 수신: /topic/chat/{chatRoomId}
    @MessageMapping("/chat/message")
    public void sendMessage(
            @Valid ChatMessageSendRequest request,
            Principal principal) {

        if (principal == null) {
            throw new AccessDeniedException("인증이 필요합니다.");
        }

        MemberDetails memberDetails = (MemberDetails) ((Authentication) principal).getPrincipal();
        Long senderId = memberDetails.getMemberId();
        boolean isAdmin = memberDetails.isAdmin();

        // 1. 서비스 호출을 통해 메시지 저장 및 완벽한 ChatMessageResponse DTO 생성
        ChatMessageResponse response = chatService.sendMessage(
                request.getChatRoomId(),
                senderId,
                isAdmin,
                request.getProductId(),
                request.getContent(),
                request.getAttachments()
        );

        // 2-1. 해당 채팅방 구독자 전원에게 실시간 메시지 전파 (/topic/chat/{roomId})
        messagingTemplate.convertAndSend(
                "/topic/chat/" + response.getChatRoomId(),
                response
        );

        // 2-2. [관리자 대시보드 전파] 관리자 좌측 방 목록이 실시간으로 최상단 이동!
        messagingTemplate.convertAndSend("/topic/admin/rooms", response);
    }

    // 2. 실시간 읽음 커서 갱신 (@MessageMapping("/chat/read"))
    // 클라이언트 발신: /app/chat/read
    @MessageMapping("/chat/read")
    public void updateReadCursor(
            @Valid ChatRoomReadRequest request,
            Principal principal) {

        if (principal == null) {
            throw new AccessDeniedException("인증이 필요합니다.");
        }

        MemberDetails memberDetails = (MemberDetails) ((Authentication) principal).getPrincipal();

        chatService.updateReadCursor(
                request.getChatRoomId(),
                memberDetails.getMemberId(),
                memberDetails.isAdmin(),
                request.getLastReadMessageId()
        );

        // 읽음 처리 완료 후 상대방 화면에 읽음 이벤트 전파 (/topic/chat/{roomId}/read)
        Object readPayload = Map.of(
                "chatRoomId", request.getChatRoomId(),
                "lastReadMessageId", request.getLastReadMessageId(),
                "readerSide", memberDetails.isAdmin() ? "ADMIN" : "CUSTOMER"
        );

        messagingTemplate.convertAndSend(
                "/topic/chat/" + request.getChatRoomId() + "/read",
                readPayload
        );
    }
}
