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
public class ChatRoomReadCursor {
    private Long id; // 읽기 커서 ID
    private Long chatRoomId; // 채팅방 ID
    private ChatReaderSide readerSide; // 읽은 주체
    private Long lastReadMessageId; // 마지막으로 읽은 메시지 ID
    private LocalDateTime lastReadAt; // 마지막 읽음 처리 시각
    private LocalDateTime createdAt; // 생성 시각
    private LocalDateTime updatedAt; // 수정 시각
}