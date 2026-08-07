package com.cakeshop.domain.chat.dto.view;

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
public class ChatRoomSidePanelResponse {
    // 1. 고객 특이사항 메모 정보
    private CustomerAdminNoteResponse note;       // 메모 DTO (없으면 null)
    
    // 2. 우측 배너 연동 주문 카드 목록
    private List<ChatRoomOrderResponse> orders;   // 연결된 주문 내역 목록
}
