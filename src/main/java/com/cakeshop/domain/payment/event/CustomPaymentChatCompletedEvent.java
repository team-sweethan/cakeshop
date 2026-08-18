package com.cakeshop.domain.payment.event;

/**
 * ******************************
 * 작성자 : 김민정
 * 담당자 : 주환
 * 작성일 : 2026-08-17
 * 기능 : 주문제작 결제 완료 후 채팅방 연동 전용 도메인 이벤트
 * 설명 : 주문제작 케이크의 결제가 완료되었을 때 채팅방의 연동 주문 목록을 실시간으로 갱신하기 위해 발행하는 이벤트 계약이다.
 * ******************************
 */
public record CustomPaymentChatCompletedEvent(long orderId) {
}
