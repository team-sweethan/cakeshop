package com.cakeshop.domain.chat.entity;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;


@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatRoomOrder {
    private Long id; // 채팅방-주문 연결 ID
    private Long chatRoomId; // 채팅방 ID
    private Long orderId; // 주문 ID
    private Long conversationAnchorMessageId; // 주문 관련 대화 시작 메시지
    private LocalDateTime createdAt; // 주문 연결 시작
}