package com.cakeshop.domain.chat.service;

import com.cakeshop.domain.chat.dto.form.ChatMessageAttachmentRequest;
import com.cakeshop.domain.chat.dto.form.CustomerAdminNoteRequest;
import com.cakeshop.domain.chat.dto.view.ChatMessageResponse;
import com.cakeshop.domain.chat.dto.view.ChatRoomListResponse;
import com.cakeshop.domain.chat.dto.view.ChatRoomOrderResponse;
import com.cakeshop.domain.chat.dto.view.ChatRoomSidePanelResponse;
import com.cakeshop.domain.chat.dto.view.ChatUnreadCountDto;
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
import com.cakeshop.domain.chat.error.ChatErrorCode;
import com.cakeshop.domain.chat.mapper.ChatMapper;
import com.cakeshop.domain.member.service.MemberChatQueryService;
import com.cakeshop.domain.order.dto.view.OrderChatView;
import com.cakeshop.domain.order.service.OrderChatQueryService;
import com.cakeshop.domain.product.service.ProductChatQueryService;
import com.cakeshop.global.common.paging.PageRequest;
import com.cakeshop.global.error.BusinessException;
import com.cakeshop.global.error.CommonErrorCode;
import com.cakeshop.global.infra.FileStorageClient;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;


@Service
@RequiredArgsConstructor
public class ChatService {
    // TODO: 고객당 채팅방 1개 UNIQUE, 방 소유자 검증

    private final ChatMapper chatMapper;
    private final MemberChatQueryService memberChatQueryService;
    private final OrderChatQueryService orderChatQueryService;
    private final FileStorageClient fileStorageClient;
    private final ChatImageValidator chatImageValidator;
    private final ProductChatQueryService productChatQueryService;

    // ==========================================
    // 0. 검증 헬퍼 메서드
    // ==========================================
    private void validateRoomAccess(ChatRoom room, Long currentUserId, boolean isAdmin) {
        if (room == null) {
            throw new BusinessException(ChatErrorCode.ROOM_NOT_FOUND);
        }
        if (!isAdmin) {
            if (currentUserId == null || !currentUserId.equals(room.getCustomerId())) {
                throw new AccessDeniedException("해당 채팅방에 대한 접근 권한이 없습니다.");
            }
        }
    }

    // ==========================================
    // 1. 공통 & 고객용 기능 (Customer)
    // ==========================================
    
    // 없으면 방 만들고, 있으면 만들어져있는거 반환 (동시성 충돌 발생 시 기존 방 흡수)
    @Transactional
    public ChatRoom getOrMakeChatRoom(Long customerId) {
        if (customerId == null || customerId <= 0) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }
        if (!memberChatQueryService.existsCustomer(customerId)) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }
        ChatRoom chatRoom = chatMapper.findChatRoomByCustomerId(customerId);

        if (chatRoom != null) {
            return chatRoom;
        }

        chatRoom = ChatRoom.builder()
            .customerId(customerId)
            .status(ChatRoomStatus.OPEN)
            .responseStatus(ChatResponseStatus.WAITING_ADMIN)
            .createdAt(LocalDateTime.now())
            .build();
        try {
            chatMapper.insertChatRoom(chatRoom);
            return chatRoom;
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            ChatRoom existingRoom = chatMapper.findChatRoomByCustomerId(customerId);
            if (existingRoom != null) {
                return existingRoom;
            }
            throw e;
        }
    }

    // 메시지 만들고 저장, 방 상태 갱신
    @Transactional
    public ChatMessage createMessage(Long roomId, Long senderId, boolean isAdmin, Long productId, 
        String content, List<ChatMessageAttachmentRequest> attachments) {

            // 1. 텅 빈 메시지 저장 차단 및 첨부파일 최대 5개 상한선 제한
            boolean hasContent = content != null && !content.trim().isEmpty();
            boolean hasAttachments = attachments != null && !attachments.isEmpty();
            if (!hasContent && !hasAttachments) {
                throw new BusinessException(CommonErrorCode.INVALID_INPUT);
            }
            if (hasAttachments) {
                if (attachments.size() > 5) {
                    throw new BusinessException(CommonErrorCode.INVALID_INPUT);
                }
                if (attachments.stream().anyMatch(Objects::isNull)) {
                    throw new BusinessException(CommonErrorCode.INVALID_INPUT);
                }
            }

            // 2. 문의 상품(productId) 존재 및 공개 상태 검증 (0 또는 음수 ID 거절)
            if (productId != null) {
                if (productId <= 0) {
                    throw new BusinessException(CommonErrorCode.INVALID_INPUT);
                }
                productChatQueryService.validateProductForChat(productId);
            }

            // 3. 채팅방 존재 여부 및 실제 인증 권한(isAdmin) 검증
            ChatRoom chatRoom = chatMapper.findChatRoomById(roomId);
            validateRoomAccess(chatRoom, senderId, isAdmin);

            ChatMessage message = ChatMessage.builder()
                .chatRoomId(roomId)
                .senderId(senderId)
                .productId(productId)
                .content(content)
                .createdAt(LocalDateTime.now())
                .build();
            chatMapper.insertChatMessage(message);

            // 첨부파일 저장
            if (hasAttachments) {
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
                isAdmin ? ChatResponseStatus.WAITING_CUSTOMER : ChatResponseStatus.WAITING_ADMIN
            );

            return message;
        }

    // S3 저장소에 이미지 파일 직접 업로드 (프론트가 파일 객체 직접 보낼 때)
    public String uploadChatImageToS3(MultipartFile file) {
        // 채팅 전용 이미지 파일 검증기 사용 (확장자, MIME 타입, 파일 시그니처, 5MB 크기 검증)
        chatImageValidator.validate(file);
        return fileStorageClient.store(file, "chat");
    }

    // 채팅방 연동 주문 목록 조회 (권한 검증 포함)
    @Transactional(readOnly = true)
    public List<ChatRoomOrderResponse> getChatRoomOrders(Long chatRoomId, Long currentUserId, boolean isAdmin) {
        ChatRoom room = chatMapper.findChatRoomById(chatRoomId);
        validateRoomAccess(room, currentUserId, isAdmin);

        List<ChatRoomOrder> roomOrders = chatMapper.findChatRoomOrderByChatRoomId(chatRoomId);
        if (roomOrders == null || roomOrders.isEmpty()) {
            return Collections.emptyList();
        }

        return roomOrders.stream().map(ro -> {
            OrderChatView order;
            try {
                order = orderChatQueryService.findOrder(ro.getOrderId());
            } catch (Exception e) {
                order = null;
            }

            return ChatRoomOrderResponse.builder()
                    .orderId(ro.getOrderId())
                    .orderNumber(order != null && order.orderNumber() != null ? order.orderNumber() : "ORD-UNKNOWN")
                    .productName(order != null && order.representativeProductName() != null ? order.representativeProductName() : "연동 주문 상품")
                    .productType(order != null && order.orderType() != null ? order.orderType() : "GENERAL")
                    .totalAmount(order != null && order.finalAmount() != null ? order.finalAmount() : BigDecimal.ZERO)
                    .orderStatus(order != null && order.status() != null ? order.status() : "UNKNOWN")
                    .pickupDateTime(order != null ? order.pickupAt() : null)
                    .conversationAnchorMessageId(ro.getConversationAnchorMessageId())
                    .createdAt(ro.getCreatedAt())
                    .build();
        }).collect(Collectors.toList());
    }

    // 채팅방에 주문 연동 (검증 포함)
    @Transactional
    public void linkOrderToChatRoom(Long chatRoomId, Long orderId, Long anchorMessageId, Long currentUserId, boolean isAdmin) {
        ChatRoom chatRoom = chatMapper.findChatRoomById(chatRoomId);
        validateRoomAccess(chatRoom, currentUserId, isAdmin);

        // anchorMessageId가 전달된 경우, 비양수 거절 및 해당 메시지가 이 채팅방의 메시지인지 검증!
        if (anchorMessageId != null) {
            if (anchorMessageId <= 0) {
                throw new BusinessException(CommonErrorCode.INVALID_INPUT);
            }
            ChatMessage anchorMsg = chatMapper.findChatMessageById(anchorMessageId);
            if (anchorMsg == null || !chatRoomId.equals(anchorMsg.getChatRoomId())) {
                throw new BusinessException(CommonErrorCode.INVALID_INPUT);
            }
        }

        // orderId 비양수 거절, 존재 여부 검증 및 OrderChatQueryService를 통한 소유권 검증
        if (orderId == null || orderId <= 0) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }
        OrderChatView order = orderChatQueryService.findOrder(orderId);
        if (order == null) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }
        if (!chatRoom.getCustomerId().equals(order.memberId())) {
            throw new AccessDeniedException("해당 채팅방 고객의 주문만 연동할 수 있습니다.");
        }

        ChatRoomOrder roomOrder = ChatRoomOrder.builder()
                .chatRoomId(chatRoomId)
                .orderId(orderId)
                .conversationAnchorMessageId(anchorMessageId)
                .createdAt(LocalDateTime.now())
                .build();

        chatMapper.upsertChatRoomOrder(roomOrder);
    }

    // 고객·관리자 읽음 커서 갱신 (Derived Side 기반 해킹 차단)
    @Transactional
    public void updateReadCursor(Long chatRoomId, Long currentUserId, boolean isAdmin, Long lastReadMessageId) {
        if (lastReadMessageId == null || lastReadMessageId <= 0) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }

        ChatRoom room = chatMapper.findChatRoomById(chatRoomId);
        validateRoomAccess(room, currentUserId, isAdmin);

        // 검증: lastReadMessageId가 진짜 해당 채팅방의 메시지인지 확인!
        ChatMessage lastMessage = chatMapper.findChatMessageById(lastReadMessageId);
        if (lastMessage == null || !chatRoomId.equals(lastMessage.getChatRoomId())) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }

        // derivedSide: 파라미터 readerSide 대신 실제 로그인 사용자의 권한(isAdmin)으로 결정!
        ChatReaderSide derivedSide = isAdmin ? ChatReaderSide.ADMIN : ChatReaderSide.CUSTOMER;

        ChatRoomReadCursor cursor = ChatRoomReadCursor.builder()
                .chatRoomId(chatRoomId)
                .readerSide(derivedSide)
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
    // 관리자 좌측 배너 채팅방 목록 조회 (탭 필터링 + PageRequest 정규화 페이징 + 미읽음 배치 집계)
    @Transactional(readOnly = true)
    public List<ChatRoomListResponse> getAdminChatRooms(ChatResponseStatus responseStatus, int page, int size) {
        PageRequest pageRequest = new PageRequest(page, size);
        int offset = pageRequest.getOffset();
        int safeSize = pageRequest.getSize();
        
        // 1. DB에서 채팅방 목록 가져오기
        List<ChatRoom> rooms = chatMapper.adminChatRoomList(responseStatus, offset, safeSize);
        if (rooms == null || rooms.isEmpty()) {
            return Collections.emptyList();
        }

        // 2. 고객 이름 배치 조회 (중복 customerId 조회를 최소화하여 N+1 방지)
        Set<Long> uniqueCustomerIds = rooms.stream().map(ChatRoom::getCustomerId).collect(Collectors.toSet());
        Map<Long, String> customerNameMap = uniqueCustomerIds.stream().collect(Collectors.toMap(
            id -> id,
            id -> memberChatQueryService.getCustomerName(id),
            (a, b) -> a
        ));

        // 3. 마지막 메시지 일괄 배치 조회 (방 20개당 20번 단건 쿼리 나가던 N+1 문제 완전 해소!)
        List<Long> lastMessageIds = rooms.stream()
                .map(ChatRoom::getLastMessageId)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        List<ChatMessage> lastMessages = !lastMessageIds.isEmpty()
                ? chatMapper.findMessagesByIds(lastMessageIds)
                : Collections.emptyList();

        Map<Long, ChatMessage> lastMessageMap = lastMessages.stream()
                .collect(Collectors.toMap(ChatMessage::getId, m -> m, (a, b) -> a));

        // 4. 안 읽은 메시지 수 일괄 GROUP BY 배치 조회 (방 100개당 100번 N+1 쿼리 나가던 문제 완벽 제거!)
        List<Long> roomIds = rooms.stream().map(ChatRoom::getId).collect(Collectors.toList());
        List<ChatUnreadCountDto> unreadDtos = chatMapper.countUnreadMessagesByRoomIds(roomIds);
        Map<Long, Integer> unreadCountMap = (unreadDtos != null && !unreadDtos.isEmpty())
                ? unreadDtos.stream().collect(Collectors.toMap(ChatUnreadCountDto::getChatRoomId, ChatUnreadCountDto::getUnreadCount, (a, b) -> a))
                : Collections.emptyMap();

        // 5. 각 채팅방을 ChatRoomListResponse DTO로 변환
        return rooms.stream().map(room -> {
            // 마지막 메시지 정보 Map에서 꺼내기
            ChatMessage lastMessage = room.getLastMessageId() != null 
                    ? lastMessageMap.get(room.getLastMessageId()) 
                    : null;

            // 이미지만 올린 메시지의 경우 미리보기 대체 문구("(사진)") 적용
            String previewContent = "";
            if (lastMessage != null) {
                if (lastMessage.getContent() != null && !lastMessage.getContent().isBlank()) {
                    previewContent = lastMessage.getContent();
                } else {
                    previewContent = "(사진)";
                }
            }

            int unreadCount = unreadCountMap.getOrDefault(room.getId(), 0);
            String customerName = customerNameMap.getOrDefault(room.getCustomerId(), "고객");

            // DTO 조립
            return ChatRoomListResponse.builder()
                    .chatRoomId(room.getId())
                    .customerId(room.getCustomerId())
                    .customerName(customerName)
                    .responseStatus(room.getResponseStatus())
                    .lastMessageContent(previewContent)
                    .lastMessageCreatedAt(lastMessage != null ? lastMessage.getCreatedAt() : room.getCreatedAt())
                    .unreadCount(unreadCount)
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
    public ChatRoomSidePanelResponse getAdminSidePanel(Long chatRoomId, Long currentUserId, boolean isAdmin) {
        ChatRoom room = chatMapper.findChatRoomById(chatRoomId);
        validateRoomAccess(room, currentUserId, isAdmin);

        // 1. 고객 메모 조회
        CustomerAdminNote noteEntity = chatMapper.findCustomerAdminNoteByCustomerId(room.getCustomerId());
        CustomerAdminNoteResponse noteResponse = (noteEntity != null)
                ? CustomerAdminNoteResponse.builder()
                        .content(noteEntity.getContent())
                        .build()
                : null;

        // 2. 연동 주문 목록 조회
        List<ChatRoomOrderResponse> orders = getChatRoomOrders(chatRoomId, currentUserId, isAdmin);

        return ChatRoomSidePanelResponse.builder()
                .note(noteResponse)
                .orders(orders)
                .build();
    }

    // 대화 내역 조회 (PageRequest 정규화 페이징 적용)
    @Transactional(readOnly = true)
    public List<ChatMessageResponse> getChatMessages(Long chatRoomId, Long currentUserId, boolean isAdmin, int page, int size) {
        ChatRoom chatRoom = chatMapper.findChatRoomById(chatRoomId);
        validateRoomAccess(chatRoom, currentUserId, isAdmin);

        PageRequest pageRequest = new PageRequest(page, size);
        int offset = pageRequest.getOffset();
        int safeSize = pageRequest.getSize();

        // 상대방의 읽음 커서 조회
        ChatReaderSide opponentSide = isAdmin ? ChatReaderSide.CUSTOMER : ChatReaderSide.ADMIN;
        ChatRoomReadCursor opponentCursor = chatMapper.findReadCursor(chatRoomId, opponentSide);
        Long opponentLastReadId = (opponentCursor != null && opponentCursor.getLastReadMessageId() != null)
                ? opponentCursor.getLastReadMessageId()
                : 0L;
        
        // 메시지 목록 조회
        List<ChatMessage> messages = chatMapper.findMessageByChatRoomId(chatRoomId, offset, safeSize);
        if (messages == null || messages.isEmpty()) {
            return Collections.emptyList();
        }

        // 메시지 ID 목록으로 첨부파일 일괄 배치 조회 (N+1 쿼리 완벽 해소)
        List<Long> messageIds = messages.stream().map(ChatMessage::getId).collect(Collectors.toList());
        List<ChatMessageAttachment> allAttachments = chatMapper.findAttachmentsByMessageIds(messageIds);
        Map<Long, List<ChatMessageAttachment>> attachmentMap = (allAttachments != null && !allAttachments.isEmpty())
                ? allAttachments.stream().collect(Collectors.groupingBy(ChatMessageAttachment::getChatMessageId))
                : Collections.emptyMap();

        return messages.stream().map(msg -> {
            List<ChatMessageAttachment> attachments = attachmentMap.getOrDefault(msg.getId(), Collections.emptyList());
            
            // 메시지 첨부 이미지 S3 / 로컬 안전 URL 변환 (외부 해커 트래킹 URL 거부)
            List<String> imageUrls = !attachments.isEmpty()
                    ? attachments.stream()
                            .map(att -> {
                                String key = att.getObjectKey();
                                if (key == null || key.isBlank()) return "";
                                // 1. 로컬 저장소 경로 (/uploads/...)
                                if (key.startsWith("/uploads/")) return key;
                                // 2. S3 공개 저장소 버킷 주소
                                if (key.startsWith("https://sweethan-cakeshop-images.s3.ap-northeast-2.amazonaws.com/")) return key;
                                // 3. 외부 도메인(http:// 또는 https://)으로 시작하는 해커 트래킹 URL은 무조건 거부!
                                if (key.startsWith("http://") || key.startsWith("https://")) return "";
                                // 4. 상대 경로 key인 경우 S3 버킷 주소 결합
                                return "https://sweethan-cakeshop-images.s3.ap-northeast-2.amazonaws.com/" + key;
                            })
                            .filter(url -> !url.isBlank())
                            .collect(Collectors.toList())
                    : Collections.emptyList();

            boolean isRead = (msg.getId() <= opponentLastReadId);

            // 발신자 유형 및 이름 명시적 매핑
            boolean isCustomerSender = msg.getSenderId() != null && msg.getSenderId().equals(chatRoom.getCustomerId());
            String senderType = isCustomerSender ? "CUSTOMER" : "ADMIN";
            String senderName = isCustomerSender ? "고객" : "관리자";

            return ChatMessageResponse.builder()
                    .id(msg.getId())
                    .chatRoomId(msg.getChatRoomId())
                    .senderId(msg.getSenderId())
                    .senderName(senderName)
                    .senderType(senderType)
                    .productId(msg.getProductId())
                    .content(msg.getContent())
                    .imageUrls(imageUrls)
                    .isRead(isRead)
                    .createdAt(msg.getCreatedAt())
                    .build();
        }).collect(Collectors.toList());
    }
}
