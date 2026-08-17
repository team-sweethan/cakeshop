package com.cakeshop.domain.chat.dto.view;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;
import java.util.List;
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
public class ChatMessageResponse {
    private Long id;                                   // 메시지 ID
    private Long chatRoomId;                           // 채팅방 ID
    private Long customerId;                           // 채팅방 고객 회원 ID
    private String customerName;                       // 채팅방 고객 이름
    private Long senderId;                             // 발신자 회원 ID
    private String senderName;                         // 발신자 이름 (예: "홍길동", "관리자")
    private String senderType;                         // 발신자 유형 ("CUSTOMER", "ADMIN")
    private String content;                            // 메시지 텍스트 내용
    @JsonProperty("isRead")
    private boolean isRead;                            // 상대방 읽음 여부
    private LocalDateTime createdAt;                   // 메시지 전송 시각
    
    // 첨부 이미지 및 문의 상품 정보
    private Long productId;                            // 문의 상품 ID (선택)
    private String productName;                        // 문의 상품명 (선택)
    private String productImageUrl;                    // 문의 상품 대표 이미지 (선택)
    private List<String> imageUrls; // S3 Presigned 이미지 전체 URL 목록 (순서 보장됨)
}
