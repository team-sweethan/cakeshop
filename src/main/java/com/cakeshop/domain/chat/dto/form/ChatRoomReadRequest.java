package com.cakeshop.domain.chat.dto.form;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatRoomReadRequest {
    @NotNull(message = "채팅방 ID는 필수입니다.")
    @Positive(message = "채팅방 ID는 양수여야 합니다.")
    private Long chatRoomId; // 읽음 처리할 채팅방 ID

    @NotNull(message = "마지막으로 읽은 메시지 ID는 필수입니다.")
    @Positive(message = "마지막으로 읽은 메시지 ID는 양수여야 합니다.")
    private Long lastReadMessageId;  // 해당 사용자가 마지막으로 읽은 메시지 ID
}
