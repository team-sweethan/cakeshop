package com.cakeshop.domain.notification.event;

/**
 * ******************************
 * 작성자 : 김민정
 * 담당자 : 김민정
 * 작성일 : 2026-08-20
 * 기능 : 주문제작 반려 후 채팅 메시지 자동 발송 트리거 이벤트
 * 설명 : CUSTOM_ORDER_REJECTED 알림 저장 완료 후 발행되어, ChatEventListener가
 *        반려 사유를 고객 채팅방에 시스템 메시지로 자동 발송하도록 트리거한다.
 * ******************************
 */
public record CustomOrderRejectedChatEvent(long orderId, long customerId) {
}
