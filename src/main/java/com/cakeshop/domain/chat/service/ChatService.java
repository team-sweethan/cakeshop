package com.cakeshop.domain.chat.service;

import com.cakeshop.domain.chat.dto.form.ChatMessageAttachmentRequest;
import com.cakeshop.domain.chat.dto.form.CustomerAdminNoteRequest;
import com.cakeshop.domain.chat.dto.view.ChatMessageResponse;
import com.cakeshop.domain.chat.dto.view.ChatRoomListResponse;
import com.cakeshop.domain.chat.dto.view.ChatRoomOrderResponse;
import com.cakeshop.domain.chat.dto.view.ChatRoomSidePanelResponse;
import com.cakeshop.domain.chat.dto.view.CustomerAdminNoteResponse;
import com.cakeshop.domain.chat.entity.ChatMessage;
import com.cakeshop.domain.chat.entity.ChatMessageAttachment;
import com.cakeshop.domain.chat.entity.ChatReaderSide;
import com.cakeshop.domain.chat.entity.ChatResponseStatus;
import com.cakeshop.domain.chat.entity.ChatRoom;
import com.cakeshop.domain.chat.entity.ChatRoomOrder;
import com.cakeshop.domain.chat.entity.ChatRoomReadCursor;
import com.cakeshop.domain.chat.entity.ChatRoomStatus;
import com.cakeshop.domain.chat.entity.CustomerAdminNote;
import com.cakeshop.domain.chat.mapper.ChatMapper;
import com.cakeshop.domain.member.dto.view.MemberAdminDetailRow;
import com.cakeshop.domain.member.mapper.MemberMapper;
import com.cakeshop.domain.order.entity.Order;
import com.cakeshop.domain.order.mapper.OrderMapper;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;


@Service
@RequiredArgsConstructor
public class ChatService {
    // TODO: 고객당 채팅방 1개 UNIQUE, 방 소유자 검증

    private final ChatMapper chatMapper;
    private final MemberMapper memberMapper;
    private final OrderMapper orderMapper;

    // ==========================================
    // 1. 공통 & 고객용 기능 (Customer)
    // ==========================================
    
    // 없으면 방 만들고, 있으면 만들어져있는거 반환
    @Transactional
    public ChatRoom getOrMakeChatRoom(Long customerId) {
        ChatRoom chatRoom = chatMapper.findChatRoomByCustomerId(customerId);

        if (chatRoom != null) {
            return chatRoom;
        }

        else {
            chatRoom = ChatRoom.builder()
                .customerId(customerId)
                .status(ChatRoomStatus.OPEN)
                .responseStatus(ChatResponseStatus.WAITING_ADMIN)
                .createdAt(LocalDateTime.now())
                .build();
            chatMapper.insertChatRoom(chatRoom);

            return chatRoom;
        }
    }

    // 메시지 만들고 저장, 방 상태 갱신
    @Transactional
    public ChatMessage createMessage(Long roomId, Long senderId, Long productId, 
        String content, List<ChatMessageAttachmentRequest> attachments) {

            // 채팅방 존재 여부 조회 (발신자가 고객인지 확인하기 위함)
            ChatRoom chatRoom = chatMapper.findChatRoomById(roomId);
            
            if (chatRoom == null) {
                throw new IllegalArgumentException("존재하지 않는 채팅방입니다.");
            }

            ChatMessage message = ChatMessage.builder()
                .chatRoomId(roomId)
                .senderId(senderId)
                .productId(productId)
                .content(content)
                .createdAt(LocalDateTime.now())
                .build();
            chatMapper.insertChatMessage(message);

            // 첨부파일 저장
            if (attachments != null && !attachments.isEmpty()) {
                int displayOrder = 0;
                for (ChatMessageAttachmentRequest att : attachments) {
                    ChatMessageAttachment attachment = ChatMessageAttachment.builder()
                        .chatMessageId(message.getId()) // 방금 DB에 저장되고 생성된 메시지 PK ID!
                        .objectKey(att.getObjectKey())
                        .originalFilename(att.getOriginalFilename())
                        .contentType(att.getContentType())
                        .fileSize(att.getFileSize())
                        .displayOrder(displayOrder++)   // 0, 1, 2... 정렬 순서 자동 증가
                        .createdAt(LocalDateTime.now())
                        .build();
                        
                    chatMapper.insertChatMessageAttachment(attachment); // 첨부파일 매퍼 호출!
                }
            }

            chatMapper.updateChatRoomLastMessage(
                roomId,
                message.getId(),
                message.getCreatedAt(),
                senderId == null || chatRoom.getCustomerId().equals(senderId)
                    ? ChatResponseStatus.WAITING_ADMIN
                    : ChatResponseStatus.WAITING_CUSTOMER
            );

            return message;
        }

    // 해당 고객 주문 내역 조회
    // 채팅방 우측 패널 연동 주문 목록 조회 (공통)
    @Transactional(readOnly = true) // 주문 매퍼 사용함
    public List<ChatRoomOrderResponse> getChatRoomOrders(Long chatRoomId) {
        // 1. DB에서 채팅방에 연동된 주문 연결 행 목록 조회
        List<ChatRoomOrder> roomOrders = chatMapper.findChatRoomOrderByChatRoomId(chatRoomId);

        // 2. 연동된 주문이 없으면 빈 목록 반환
        if (roomOrders == null || roomOrders.isEmpty()) {
            return Collections.emptyList();
        }

        // 3. 연동된 주문이 있으면 Order 정보와 조합하여 ChatRoomOrderResponse DTO로 변환하여 반환
        return roomOrders.stream()
                .map(this::convertToOrderResponse)
                .collect(Collectors.toList());
    }

    // ChatRoomOrder -> ChatRoomOrderResponse 변환 헬퍼 메서드
    private ChatRoomOrderResponse convertToOrderResponse(ChatRoomOrder roomOrder) {
        // OrderMapper를 통해 실제 주문 기본 정보 조회
        Order order = orderMapper.findOrderById(roomOrder.getOrderId()).orElse(null);

        return ChatRoomOrderResponse.builder()
                .orderId(roomOrder.getOrderId())
                .orderNumber(order != null ? order.getOrderNumber() : "")
                .productName(order != null ? "연동 주문 상품" : "")
                .productType(order != null ? "GENERAL" : "CUSTOM")
                .totalAmount(order != null ? order.getFinalAmount() : BigDecimal.ZERO)
                .orderStatus(order != null && order.getStatus() != null ? order.getStatus().name() : "")
                .pickupDateTime(order != null ? order.getPickupAt() : null)
                .conversationAnchorMessageId(roomOrder.getConversationAnchorMessageId())
                .createdAt(roomOrder.getCreatedAt())
                .build();
    }

    // 채팅방에 주문 연동 저장 (신규 주문 연결)
    @Transactional
    public void linkOrderToChatRoom(Long chatRoomId, Long orderId, Long anchorMessageId) {
        ChatRoomOrder roomOrder = ChatRoomOrder.builder()
                .chatRoomId(chatRoomId)
                .orderId(orderId)
                .conversationAnchorMessageId(anchorMessageId) // 대화 앵커 메시지 ID (선택)
                .createdAt(LocalDateTime.now())
                .build();

        chatMapper.upsertChatRoomOrder(roomOrder); // 👈 매퍼 호출해서 DB에 연동 저장!
    }

    // 고객·관리자 읽음 커서 저장/갱신 (공통)
    @Transactional
    public void updateReadCursor(Long chatRoomId, Long lastReadMessageId, ChatReaderSide readerSide) {
        ChatRoomReadCursor cursor = ChatRoomReadCursor.builder()
                .chatRoomId(chatRoomId)
                .readerSide(readerSide)
                .lastReadMessageId(lastReadMessageId)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        chatMapper.upsertReadCursor(cursor);
    }

    // 고객·관리자 읽음 커서 조회 (공통)
    @Transactional(readOnly = true)
    public ChatRoomReadCursor getReadCursor(Long chatRoomId, ChatReaderSide readerSide) {
        return chatMapper.findReadCursor(chatRoomId, readerSide);
    }

    // ==========================================
    // 2. 관리자용 기능 (Admin)
    // ==========================================
    
    // 채팅 목록 조회
    // 관리자 좌측 배너 채팅방 목록 조회 (탭 필터링 + 페이징) // 고객 매퍼 사용
    @Transactional(readOnly = true)
    public List<ChatRoomListResponse> getAdminChatRooms(ChatResponseStatus responseStatus, int page, int size) {
        int offset = Math.max(0, (page - 1) * size);
        
        // 1. DB에서 채팅방 목록 가져오기
        List<ChatRoom> rooms = chatMapper.adminChatRoomList(responseStatus, offset, size);
        if (rooms == null || rooms.isEmpty()) {
            return Collections.emptyList();
        }

        // 2. 각 채팅방을 ChatRoomListResponse DTO로 변환
        return rooms.stream().map(room -> {
            // 마지막 메시지 정보 조회
            ChatMessage lastMessage = room.getLastMessageId() != null 
                    ? chatMapper.findChatMessageById(room.getLastMessageId()) 
                    : null;

            // 관리자의 읽음 커서 위치 조회
            ChatRoomReadCursor adminCursor = chatMapper.findReadCursor(room.getId(), ChatReaderSide.ADMIN);
            Long lastReadMessageId = (adminCursor != null) ? adminCursor.getLastReadMessageId() : 0L;

            // DTO 조립
            return ChatRoomListResponse.builder()
                    .chatRoomId(room.getId())
                    .customerId(room.getCustomerId())
                    .customerName(memberMapper.findAdminMemberDetail(room.getCustomerId())
                            .map(MemberAdminDetailRow::name)
                            .orElse("고객"))
                    .responseStatus(room.getResponseStatus())
                    .lastMessageContent(lastMessage != null ? lastMessage.getContent() : "")
                    .lastMessageCreatedAt(lastMessage != null ? lastMessage.getCreatedAt() : room.getCreatedAt())
                    .unreadCount(0) // 💡 안 읽은 메시지 수 매핑
                    .build();
        }).collect(Collectors.toList());
    }

    // 관리자 특이사항 메모 저장/수정
    @Transactional
    public void saveCustomerAdminNote(Long customerId, CustomerAdminNoteRequest request, Long adminId) {
        CustomerAdminNote note = CustomerAdminNote.builder()
                .customerId(customerId)
                .content(request.getContent())
                .updatedBy(adminId)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        chatMapper.upsertCustomerAdminNote(note);
    }

    // 관리자 우측 패널 정보 조회 (고객 메모 + 연동 주문 카드 목록)
    @Transactional(readOnly = true)
    public ChatRoomSidePanelResponse getAdminSidePanel(Long chatRoomId) {
        ChatRoom room = chatMapper.findChatRoomById(chatRoomId);
        if (room == null) {
            throw new IllegalArgumentException("존재하지 않는 채팅방입니다.");
        }

        // 1. 고객 메모 조회
        CustomerAdminNote noteEntity = chatMapper.findCustomerAdminNoteByCustomerId(room.getCustomerId());
        CustomerAdminNoteResponse noteResponse = (noteEntity != null)
                ? CustomerAdminNoteResponse.builder()
                        .content(noteEntity.getContent())
                        .build()
                : null;

        // 2. 연동 주문 목록 조회
        List<ChatRoomOrderResponse> orders = getChatRoomOrders(chatRoomId);

        return ChatRoomSidePanelResponse.builder()
                .note(noteResponse)
                .orders(orders)
                .build();
    }

    // 대화 내역 조회
    @Transactional(readOnly = true)
    public List<ChatMessageResponse> getChatMessages(Long chatRoomId, Long currentUserId, int page, int size) {
        int offset = Math.max(0, (page - 1) * size);
        
        // 메시지 목록 조회
        List<ChatMessage> messages = chatMapper.findMessageByChatRoomId(chatRoomId, offset, size);
        if (messages == null || messages.isEmpty()) {
            return Collections.emptyList();
        }

        return messages.stream().map(msg -> {
            // 메시지 첨부 이미지 S3 Object Key 목록 조회
            List<ChatMessageAttachment> attachments = chatMapper.findAttachmentsByChatMessageId(msg.getId());
            List<String> imageUrls = (attachments != null && !attachments.isEmpty())
                    ? attachments.stream().map(ChatMessageAttachment::getObjectKey).collect(Collectors.toList())
                    : Collections.emptyList();

            return ChatMessageResponse.builder()
                    .id(msg.getId())
                    .chatRoomId(msg.getChatRoomId())
                    .senderId(msg.getSenderId())
                    .productId(msg.getProductId())
                    .content(msg.getContent())
                    .imageUrls(imageUrls)
                    .isRead(true)
                    .createdAt(msg.getCreatedAt())
                    .build();
        }).collect(Collectors.toList());
    }
}
