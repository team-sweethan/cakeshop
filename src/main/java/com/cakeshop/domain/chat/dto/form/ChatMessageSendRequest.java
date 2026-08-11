package com.cakeshop.domain.chat.dto.form;

import java.util.List;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
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
public class ChatMessageSendRequest {
    @NotNull(message = "채팅방 ID는 필수입니다.")
    private Long chatRoomId; // 채팅방 id

    private String content; // 보내는 메시지 내용
    private Long productId; // 문의 대상 상품 id (선택)

    @Valid
    @Size(max = 5, message = "첨부파일은 메시지당 최대 5개까지만 전송 가능합니다.")
    private List<@NotNull @Valid ChatMessageAttachmentRequest> attachments; // 첨부 정보 목록 (선택)
}
