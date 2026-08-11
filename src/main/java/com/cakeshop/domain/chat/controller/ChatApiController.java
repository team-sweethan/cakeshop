package com.cakeshop.domain.chat.controller;

import com.cakeshop.domain.chat.dto.form.ChatMessageAttachmentRequest;
import com.cakeshop.domain.chat.dto.form.ChatMessageSendRequest;
import com.cakeshop.domain.chat.dto.form.CustomerAdminNoteRequest;
import com.cakeshop.domain.chat.dto.view.ChatMessageResponse;
import com.cakeshop.domain.chat.dto.view.ChatRoomListResponse;
import com.cakeshop.domain.chat.dto.view.ChatRoomOrderResponse;
import com.cakeshop.domain.chat.dto.view.ChatRoomSidePanelResponse;
import com.cakeshop.domain.chat.entity.ChatMessage;
import com.cakeshop.domain.chat.entity.ChatResponseStatus;
import com.cakeshop.domain.chat.entity.ChatRoom;
import com.cakeshop.domain.chat.service.ChatService;
import com.cakeshop.global.security.MemberDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class ChatApiController {

    private final ChatService chatService;

    // 1. 내 1:1 채팅방 조회 및 없으면 새로 생성 (고객용)
    @GetMapping("/api/chat/room")
    public ResponseEntity<ChatRoom> getOrMakeRoom(@AuthenticationPrincipal MemberDetails memberDetails) {
        if (memberDetails == null) {
            return ResponseEntity.status(401).build();
        }
        ChatRoom room = chatService.getOrMakeChatRoom(memberDetails.getMemberId());
        return ResponseEntity.ok(room);
    }

    // 관리자가 특정 고객(customerId)과의 1:1 채팅방 조회 및 신규 생성
    @GetMapping("/api/admin/chat/room")
    public ResponseEntity<ChatRoom> getOrMakeRoomAdmin(
            @RequestParam Long customerId,
            @AuthenticationPrincipal MemberDetails memberDetails) {

        if (memberDetails == null || !memberDetails.isAdmin()) {
            return ResponseEntity.status(403).build(); // 관리자 아니면 거절
        }

        ChatRoom room = chatService.getOrMakeChatRoom(customerId);
        return ResponseEntity.ok(room);
    }

    // 2. 대화 메시지 목록 조회 (고객/관리자 공용)
    @GetMapping("/api/chat/messages")
    public ResponseEntity<List<ChatMessageResponse>> getChatMessages(
            @RequestParam Long chatRoomId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal MemberDetails memberDetails) {

        if (memberDetails == null) {
            return ResponseEntity.status(401).build();
        }

        List<ChatMessageResponse> messages = chatService.getChatMessages(
                chatRoomId, memberDetails.getMemberId(), memberDetails.isAdmin(), page, size);
                
        return ResponseEntity.ok(messages);
    }



    // 3. 메시지 전송 (고객/관리자 공용)
    @PostMapping("/api/chat/messages")
    public ResponseEntity<ChatMessageResponse> sendMessage(
            @AuthenticationPrincipal MemberDetails memberDetails,
            @Valid @RequestBody ChatMessageSendRequest request) {

        if (memberDetails == null) {
            return ResponseEntity.status(401).build();
        }

        ChatMessage message = chatService.createMessage(
                request.getChatRoomId(),
                memberDetails.getMemberId(),
                memberDetails.isAdmin(),
                request.getProductId(),
                request.getContent(),
                request.getAttachments()
        );
        return ResponseEntity.ok(ChatMessageResponse.builder()
                .id(message.getId())
                .chatRoomId(message.getChatRoomId())
                .senderId(message.getSenderId())
                .productId(message.getProductId())
                .content(message.getContent())
                .createdAt(message.getCreatedAt())
                .build());
    }

    // 4. 사진 파일 업로드 API (독립 파일 업로드)
    @PostMapping("/api/chat/images")
    public ResponseEntity<String> uploadImage(@RequestParam("file") MultipartFile file) {
        String fileUrl = chatService.uploadChatImageToS3(file);
        return ResponseEntity.ok(fileUrl);
    }

    // 5. 채팅방 연동 주문 내역 배너 목록 조회 (고객용)
    @GetMapping("/api/chat/rooms/{chatRoomId}/orders")
    public ResponseEntity<List<ChatRoomOrderResponse>> getChatRoomOrders(
            @PathVariable Long chatRoomId,
            @AuthenticationPrincipal MemberDetails memberDetails) {

        if (memberDetails == null) {
            return ResponseEntity.status(401).build();
        }

        List<ChatRoomOrderResponse> orders = chatService.getChatRoomOrders(
                chatRoomId, memberDetails.getMemberId(), memberDetails.isAdmin());
                
        return ResponseEntity.ok(orders);
    }

    // 6. 관리자 좌측 채팅방 목록 조회 (관리자 전용)
    @GetMapping("/api/admin/chat/rooms")
    public ResponseEntity<List<ChatRoomListResponse>> getAdminChatRooms(
            @RequestParam(required = false) ChatResponseStatus status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal MemberDetails memberDetails) {

        if (memberDetails == null || !memberDetails.isAdmin()) {
            return ResponseEntity.status(403).build();
        }

        List<ChatRoomListResponse> rooms = chatService.getAdminChatRooms(status, page, size);
        return ResponseEntity.ok(rooms);
    }


    // 7-1. 관리자 우측 패널 조회 (고객 메모 + 주문 목록)
    @GetMapping("/api/admin/chat/rooms/{chatRoomId}/side-panel")
    public ResponseEntity<ChatRoomSidePanelResponse> getAdminSidePanel(
            @PathVariable Long chatRoomId,
            @AuthenticationPrincipal MemberDetails memberDetails) {

        if (memberDetails == null || !memberDetails.isAdmin()) {
            return ResponseEntity.status(403).build(); // 관리자 전용!
        }

        ChatRoomSidePanelResponse response = chatService.getAdminSidePanel(
                chatRoomId, memberDetails.getMemberId(), true);

        return ResponseEntity.ok(response);
    }

    // 7-2. 고객 특이사항 메모 저장 API (관리자 전용)
    @PostMapping("/api/admin/chat/customers/{customerId}/note")
    public ResponseEntity<Void> saveCustomerAdminNote(
            @PathVariable Long customerId,
            @Valid @RequestBody CustomerAdminNoteRequest request,
            @AuthenticationPrincipal MemberDetails memberDetails) {

        if (memberDetails == null || !memberDetails.isAdmin()) {
            return ResponseEntity.status(403).build();
        }

        chatService.saveCustomerAdminNote(customerId, request, memberDetails.getMemberId());
        return ResponseEntity.ok().build();
    }

    // 8. 읽음 커서 갱신 (고객/관리자 공용)
    @PatchMapping("/api/chat/read-cursor")
    public ResponseEntity<Void> updateReadCursor(
            @RequestParam Long chatRoomId,
            @RequestParam Long lastReadMessageId,
            @AuthenticationPrincipal MemberDetails memberDetails) {

        if (memberDetails == null) {
            return ResponseEntity.status(401).build();
        }

        chatService.updateReadCursor(
                chatRoomId, memberDetails.getMemberId(), memberDetails.isAdmin(), lastReadMessageId);
        return ResponseEntity.ok().build();
    }

    // 9. 채팅방에 주문 연동 (관리자 전용) (역할이 약간 애매함 필요 없으면 삭제할 것)
    @PostMapping("/api/admin/chat/orders")
    public ResponseEntity<Void> linkOrderToChatRoom(
            @RequestParam Long chatRoomId,
            @RequestParam Long orderId,
            @RequestParam(required = false) Long anchorMessageId,
            @AuthenticationPrincipal MemberDetails memberDetails) {

        if (memberDetails == null || !memberDetails.isAdmin()) {
            return ResponseEntity.status(403).build();
        }

        chatService.linkOrderToChatRoom(
                chatRoomId, orderId, anchorMessageId, memberDetails.getMemberId(), true);
                
        return ResponseEntity.ok().build();
    }

}
