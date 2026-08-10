package com.cakeshop.domain.chat.mapper;

import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.cakeshop.domain.chat.entity.ChatMessage;
import com.cakeshop.domain.chat.entity.ChatMessageAttachment;
import com.cakeshop.domain.chat.entity.ChatReaderSide;
import com.cakeshop.domain.chat.entity.ChatResponseStatus;
import com.cakeshop.domain.chat.entity.ChatRoom;
import com.cakeshop.domain.chat.entity.ChatRoomOrder;
import com.cakeshop.domain.chat.entity.ChatRoomReadCursor;
import com.cakeshop.domain.chat.entity.ChatRoomStatus;
import com.cakeshop.domain.chat.entity.CustomerAdminNote;

@Mapper
public interface ChatMapper {

    // ==========================================
    // 1. 채팅방 (chat_rooms)
    // ==========================================
    void insertChatRoom(ChatRoom chatRoom); // 새 1:1 채팅방 생성

    ChatRoom findChatRoomById(Long id); // 채팅방 단건 조회 (이미 채팅방에 입장해서 채팅방 번호를 알고 있을 때)

    ChatRoom findChatRoomByCustomerId(Long customerId); //특정 고객의 채팅방 조회 (고객이 1:1 문의하기 버튼을 눌렀을 때)

    // 메시지 발송 시 최신 메시지 ID, 시각, 답변 상태 (response_status) 갱신
    void updateChatRoomLastMessage(
        @Param("chatRoomId") Long chatRoomId,
        @Param("lastMessageId") Long lastMessageId,
        @Param("lastMessageAt") LocalDateTime lastMessageAt,
        @Param("responseStatus") ChatResponseStatus responseStatus
    );

    // 관리자 좌측 배너 목록 조회
    List<ChatRoom> adminChatRoomList(
        @Param("responseStatus") ChatResponseStatus responseStatus,
        @Param("offset") int offset,
        @Param("size") int size
    );

    // 관리자 좌측 배너 총 개수
    int countChatRoomsForAdmin(@Param("responseStatus") ChatResponseStatus responseStatus);


    // ==========================================
    // 2. 메시지 (chat_messages)
    // ==========================================
    void insertChatMessage(ChatMessage chatMessage); // 메시지 저장

    // 메시지 단건 조회
    ChatMessage findChatMessageById(Long id);

    // 채팅방 대화 목록 조회
    List<ChatMessage> findMessageByChatRoomId(
        @Param("chatRoomId") Long chatRoomId,
        @Param("offset") int offset,
        @Param("size") int size
    );

    // 안 읽은 메시지 수 계산
    int countUnreadMessages(
        @Param("chatRoomId") Long chatRoomId,
        @Param("lastReadMessageId") Long lastReadMessageId,
        @Param("readerUserId") Long readerUserId
    );


    // ==========================================
    // 3. xmr
    // ==========================================

    // 첨부파일 저장
    void insertChatMessageAttachment(ChatMessageAttachment chatMessageAttachment);

    // 특정 메시지에 딸린 모든 첨부파일 조회
    List<ChatMessageAttachment> findAttachmentsByChatMessageId(Long chatMessageId);


    // ==========================================
    // 4. 읽음 커서 (chat_room_read_cursors)
    // ==========================================

    // 고객·관리자별 읽음 커서 저장/수정
    void upsertReadCursor(ChatRoomReadCursor cursor);

    // 특정 사용자(고객·관리자)의 읽음 커서 조회
    ChatRoomReadCursor findReadCursor(
        @Param("chatRoomId") Long chatRoomId,
        @Param("readerSide") ChatReaderSide readerSide
    );


    // ==========================================
    // 5. 고객 메모 (customer_admin_notes)
    // ==========================================

    // 관리자가 특정 고객의 메모를 저장하거나 수정 ( upsert )
    void upsertCustomerAdminNote(CustomerAdminNote note);

    // 관리자가 특정 고객의 메모 조회
    CustomerAdminNote findCustomerAdminNoteByCustomerId(Long customerId);


    // ==========================================
    // 6. 연동 주문 (chat_room_orders)
    // ==========================================

    // 채팅방과 주문을 연결하거나 변경 ( upsert )
    void upsertChatRoomOrder(ChatRoomOrder chatRoomOrder);

    // 특정 채팅방에 연결된 모든 주문 조회
    List<ChatRoomOrder> findChatRoomOrderByChatRoomId(Long chatRoomId);
}
