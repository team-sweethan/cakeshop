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
public class ChatMessage {
    private Long id; // 메시지 ID
    private Long chatRoomId; // 채팅방 ID
    private Long senderId; // 실제 발신자 ID
    private Long productId; // 상품 문의 대상 상품
    private String content; // 메시지 텍스트
    private LocalDateTime createdAt; // 메시지 생성 시각
}
