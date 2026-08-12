package com.cakeshop.domain.chat.dto.view;

import com.cakeshop.domain.chat.entity.ChatResponseStatus;
import java.time.LocalDateTime;
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
public class ChatRoomListResponse {
    private Long chatRoomId;                     // 채팅방 ID (클릭 시 식별용)
    private Long customerId;                     // 고객 회원 ID
    private String customerName;                 // 고객 이름 (예: "홍길동")
    
    private ChatResponseStatus responseStatus;   // 답변 상태 배지 ("미답변", "상담중", "완료")
    
    private String lastMessageContent;           // 마지막 메시지 미리보기 텍스트
    private LocalDateTime lastMessageCreatedAt;  // 마지막 메시지 전송 시각
    private int unreadCount;                     // 안 읽은 메시지 개수 (빨간 뱃지 숫자)
    
    // 대표 연동 주문 정보 (선택)
    private Long orderId;                        // 대표 주문 ID (클릭 처리용)
    private String orderNumber;                  // 대표 주문 번호 (화면 표시용)
    private String orderStatus;                  // 대표 주문 상태
}
