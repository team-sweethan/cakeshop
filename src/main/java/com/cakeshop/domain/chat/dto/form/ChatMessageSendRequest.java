package com.cakeshop.domain.chat.dto.form;

import java.util.List;
import jakarta.validation.constraints.NotNull;
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
    // 메시지 보낼 때 담을 상자
    private Long chatRoomId; // 채팅방 id
    private String content; // 보내는 메시지 내용
    private Long productId; // 문의 대상 상품 id (선택)
    private List<String> objectKeys; // 첨부 이미지 S3 객체 키 목록 (선택)
}