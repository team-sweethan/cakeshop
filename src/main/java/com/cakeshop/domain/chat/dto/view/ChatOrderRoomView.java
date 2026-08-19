package com.cakeshop.domain.chat.dto.view;

/** 주문 화면이 관리자 채팅방을 다시 열 때 필요한 최소 식별 정보다. */
public record ChatOrderRoomView(Long chatRoomId, Long customerId) {
}
