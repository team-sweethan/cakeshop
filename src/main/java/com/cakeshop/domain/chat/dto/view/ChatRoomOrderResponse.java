package com.cakeshop.domain.chat.dto.view;

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
public class ChatRoomOrderResponse {
    private Long orderId;                         // DB PK (API 호출용)
    private String orderNumber;                   // 주문번호 (예: "ORD-20260807-001")
    private String productName;                   // 대표 상품명
    private String productType;                   // 상품 유형 ("CUSTOM": 주문제작, "GENERAL": 일반)
    
    private Integer totalAmount;                  // 결제 금액
    private String orderStatus;                   // 주문 상태 ("PAYMENT_COMPLETED", "APPROVED" 등)
    
    private LocalDateTime pickupDateTime;         // 픽업 예약 일시 (화면 작게 표시용)
    private LocalDateTime createdAt;              // 주문 생성 시각 (정렬 및 날짜 표시용)
    
    private Long conversationAnchorMessageId;     // 클릭 시 대화 위치 이동용 앵커 ID
}
