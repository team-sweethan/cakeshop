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

    // 1-1. 내 1:1 채팅방 단순 조회 (고객용, 방이 없으면 404, 생성 부작용 없음)
    @GetMapping("/api/chat/room")
    public ResponseEntity<ChatRoom> getRoomCustomer(@AuthenticationPrincipal MemberDetails memberDetails) {
        if (memberDetails == null) {
            return ResponseEntity.status(401).build();
        }
        ChatRoom room = chatService.getChatRoomByCustomerId(memberDetails.getMemberId());
        if (room == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(room);
    }

    // 1-2. 내 1:1 채팅방 신규 생성 및 보장 (고객용 POST)
    @PostMapping("/api/chat/room")
    public ResponseEntity<ChatRoom> createRoomCustomer(@AuthenticationPrincipal MemberDetails memberDetails) {
        if (memberDetails == null) {
            return ResponseEntity.status(401).build();
        }
        ChatRoom room = chatService.getOrMakeChatRoom(memberDetails.getMemberId());
        return ResponseEntity.ok(room);
    }

    // 관리자가 특정 고객(customerId)과의 1:1 채팅방 단순 조회 (방이 없으면 404, 생성 부작용 없음)
    @GetMapping("/api/admin/chat/room")
    public ResponseEntity<ChatRoom> getRoomAdmin(
            @RequestParam Long customerId,
            @AuthenticationPrincipal MemberDetails memberDetails) {

        if (memberDetails == null || !memberDetails.isAdmin()) {
            return ResponseEntity.status(403).build();
        }

        ChatRoom room = chatService.getChatRoomByCustomerId(customerId);
        if (room == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(room);
    }

    // 관리자가 특정 고객(customerId)과의 1:1 채팅방 신규 생성 및 조회 (POST)
    @PostMapping("/api/admin/chat/room")
    public ResponseEntity<ChatRoom> createOrGetRoomAdmin(
            @RequestParam Long customerId,
            @AuthenticationPrincipal MemberDetails memberDetails) {

        if (memberDetails == null || !memberDetails.isAdmin()) {
            return ResponseEntity.status(403).build();
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
        List<String> imageUrls = (request.getAttachments() != null && !request.getAttachments().isEmpty())
                ? request.getAttachments().stream()
                        .map(ChatMessageAttachmentRequest::getObjectKey)
                        .filter(java.util.Objects::nonNull)
                        .collect(java.util.stream.Collectors.toList())
                : java.util.Collections.emptyList();

        return ResponseEntity.ok(ChatMessageResponse.builder()
                .id(message.getId())
                .chatRoomId(message.getChatRoomId())
                .senderId(message.getSenderId())
                .senderType(memberDetails.isAdmin() ? "ADMIN" : "CUSTOMER")
                .productId(message.getProductId())
                .content(message.getContent())
                .imageUrls(imageUrls)
                .createdAt(message.getCreatedAt())
                .build());
    }

    // 4. 사진 파일 업로드 API (독립 파일 업로드, 인증 및 활성 회원 검증 추가)
    @PostMapping("/api/chat/images")
    public ResponseEntity<String> uploadImage(
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal MemberDetails memberDetails) {

        if (memberDetails == null) {
            return ResponseEntity.status(401).build();
        }

        String fileUrl = chatService.uploadChatImageToS3(
                file, memberDetails.getMemberId(), memberDetails.isAdmin());
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

    // 7-3. 채팅방 상태 변경 API (관리자 전용: RESOLVED 등)
    @PatchMapping("/api/admin/chat/rooms/{chatRoomId}/status")
    public ResponseEntity<Void> updateRoomStatus(
            @PathVariable Long chatRoomId,
            @RequestParam ChatResponseStatus status,
            @AuthenticationPrincipal MemberDetails memberDetails) {

        if (memberDetails == null || !memberDetails.isAdmin()) {
            return ResponseEntity.status(403).build();
        }

        chatService.updateResponseStatus(chatRoomId, status, memberDetails.getMemberId(), true);
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

    // ==========================================
    // 채팅 API 전용 예외 핸들러 (REST JSON 응답 보장)
    // ==========================================
    @ExceptionHandler(com.cakeshop.global.error.BusinessException.class)
    public ResponseEntity<java.util.Map<String, String>> handleBusinessException(com.cakeshop.global.error.BusinessException e) {
        return ResponseEntity.status(e.getErrorCode().status())
                .body(java.util.Map.of("code", e.getErrorCode().code(), "message", e.getErrorCode().message()));
    }

    @ExceptionHandler({
            org.springframework.web.bind.MethodArgumentNotValidException.class,
            org.springframework.validation.BindException.class,
            org.springframework.web.bind.MissingServletRequestParameterException.class,
            org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class,
            org.springframework.http.converter.HttpMessageNotReadableException.class
    })
    public ResponseEntity<java.util.Map<String, String>> handleValidationException() {
        return ResponseEntity.status(400)
                .body(java.util.Map.of("code", com.cakeshop.global.error.CommonErrorCode.INVALID_INPUT.code(), "message", com.cakeshop.global.error.CommonErrorCode.INVALID_INPUT.message()));
    }

    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    public ResponseEntity<java.util.Map<String, String>> handleAccessDeniedException() {
        return ResponseEntity.status(403)
                .body(java.util.Map.of("code", com.cakeshop.global.error.CommonErrorCode.FORBIDDEN.code(), "message", com.cakeshop.global.error.CommonErrorCode.FORBIDDEN.message()));
    }

}
