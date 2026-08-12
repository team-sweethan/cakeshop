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
// DB 조회 결과·도메인 상태
public class ChatRoom {
    private Long id; // 채팅방 ID
    private Long customerId; // 고객 회원 ID
    private ChatRoomStatus status; // 관리자 내부 상담 운영 상태
    private ChatResponseStatus responseStatus; // 현재 답변이 필요한 주체
    private Long lastMessageId; // 가장 최근 메시지 ID
    private LocalDateTime lastMessageAt; // 마지막 메시지 전송 시각
    private LocalDateTime createdAt; // 채팅방 생성 시각
    private LocalDateTime updatedAt; // 채팅방 정보 수정 시각
}
