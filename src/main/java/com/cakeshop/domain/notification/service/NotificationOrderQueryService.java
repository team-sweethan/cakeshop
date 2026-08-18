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
 * 설명 : 주문 도메인 등 외부 도메인이 알림 중복 여부(eventKey 멱등성)를 안전하게 조회하도록 전용 QueryService로 제공한다.
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
}
