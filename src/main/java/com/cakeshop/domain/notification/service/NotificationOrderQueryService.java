package com.cakeshop.domain.notification.service;

import com.cakeshop.domain.notification.mapper.NotificationMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * ******************************
 * 작성자 : 김민정
 * 담당자 : 김민정
 * 작성일 : 2026-08-18
 * 기능 : 주문 알림 연동 전용 조회 서비스 계약
 * 설명 : 주문 도메인 등 외부 도메인이 알림 중복 여부(eventKey 멱등성) 및 SMS 최종 성공 이력을 안전하게 조회하도록 전용 QueryService로 제공한다.
 * ******************************
 */
@Service
@RequiredArgsConstructor
public class NotificationOrderQueryService {

    private final NotificationMapper notificationMapper;

    /** 수신자와 이벤트 키 기반 알림 존재 여부 조회 */
    @Transactional(readOnly = true)
    public boolean existsByReceiverIdAndEventKey(Long receiverId, String eventKey) {
        if (receiverId == null || eventKey == null || eventKey.isBlank()) {
            return false;
        }
        return notificationMapper.existsByReceiverIdAndEventKey(receiverId, eventKey);
    }

    /** 수신자와 이벤트 키 기반 알림 생성 및 SMS 정상 발송 완료(또는 최대 재시도 2회 초과) 여부 조회 */
    @Transactional(readOnly = true)
    public boolean isNotificationFullySent(Long receiverId, String eventKey) {
        if (receiverId == null || eventKey == null || eventKey.isBlank()) {
            return false;
        }
        Long notificationId = notificationMapper.findIdByReceiverIdAndEventKey(receiverId, eventKey);
        if (notificationId == null) {
            return false;
        }
        if (notificationMapper.hasSentDelivery(notificationId)) {
            return true;
        }
        // SMS 발송 시도가 최대 횟수(2회)에 도달한 경우 추가 반복 발송 방지를 위해 완료로 간주
        return notificationMapper.countDeliveryAttempts(notificationId) >= 2;
    }
}
