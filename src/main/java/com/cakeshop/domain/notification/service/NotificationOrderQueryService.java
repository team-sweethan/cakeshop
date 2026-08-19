package com.cakeshop.domain.notification.service;

import com.cakeshop.domain.member.service.MemberNotificationQueryService;
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
    private final MemberNotificationQueryService memberNotificationQueryService;

    /** 수신자와 이벤트 키 기반 알림 존재 여부 조회 */
    @Transactional(readOnly = true)
    public boolean existsByReceiverIdAndEventKey(Long receiverId, String eventKey) {
        if (receiverId == null || eventKey == null || eventKey.isBlank()) {
            return false;
        }
        return notificationMapper.existsByReceiverIdAndEventKey(receiverId, eventKey);
    }

    /** 고객 픽업 리마인더 알림 발송 완료 여부 조회 */
    @Transactional(readOnly = true)
    public boolean isCustomerPickupReminderSent(Long customerId, Long orderId, boolean isTomorrow) {
        String typeName = isTomorrow ? "CUSTOMER_PICKUP_REMINDER_TOMORROW" : "CUSTOMER_PICKUP_REMINDER_TODAY";
        return isNotificationFullySent(customerId, typeName + ":" + customerId + ":" + orderId);
    }

    /** 관리자 픽업 리마인더 알림 발송 완료 여부 조회 */
    @Transactional(readOnly = true)
    public boolean isAdminPickupReminderSent(Long adminId, Long orderId, boolean isTomorrow) {
        String typeName = isTomorrow ? "ADMIN_PICKUP_REMINDER_TOMORROW" : "ADMIN_PICKUP_REMINDER_TODAY";
        return isNotificationFullySent(adminId, typeName + ":" + adminId + ":" + orderId);
    }

    /** 고객 픽업 완료 알림 발송 완료 여부 조회 */
    @Transactional(readOnly = true)
    public boolean isCustomerOrderPickedUpSent(Long customerId, Long orderId) {
        return isNotificationFullySent(customerId, "CUSTOMER_ORDER_PICKED_UP:" + customerId + ":" + orderId);
    }

    /** 관리자 픽업 완료 알림 발송 완료 여부 조회 */
    @Transactional(readOnly = true)
    public boolean isAdminOrderPickedUpSent(Long adminId, Long orderId) {
        return isNotificationFullySent(adminId, "ADMIN_PICKEDUP:" + adminId + ":" + orderId);
    }

    /** 고객 주문제작 제작중 알림 발송 완료 여부 조회 */
    @Transactional(readOnly = true)
    public boolean isCustomOrderInProductionSent(Long customerId, Long orderId) {
        return isNotificationFullySent(customerId, "CUSTOM_ORDER_IN_PRODUCTION:" + customerId + ":" + orderId);
    }

    /** 고객 주문제작 반려 알림 발송 완료 여부 조회 */
    @Transactional(readOnly = true)
    public boolean isCustomOrderRejectedSent(Long customerId, Long orderId) {
        return isNotificationFullySent(customerId, "CUSTOM_ORDER_REJECTED:" + customerId + ":" + orderId);
    }

    /** 고객 주문 취소 알림 발송 완료 여부 조회 */
    @Transactional(readOnly = true)
    public boolean isOrderCanceledSent(Long customerId, Long orderId) {
        return isNotificationFullySent(customerId, "ORDER_CANCELED:" + customerId + ":" + orderId);
    }

    /** 관리자 주문 취소 알림 발송 완료 여부 조회 */
    @Transactional(readOnly = true)
    public boolean isAdminOrderCanceledSent(Long adminId, Long orderId) {
        return isNotificationFullySent(adminId, "ORDER_CANCEL_REQUEST:" + adminId + ":" + orderId);
    }

    /** 수신자와 이벤트 키 기반 알림 생성 및 SMS 정상 발송 완료(또는 최대 재시도 2회 초과/비활성 회원) 여부 내부 조회 */
    @Transactional(readOnly = true)
    public boolean isNotificationFullySent(Long receiverId, String eventKey) {
        if (receiverId == null || eventKey == null || eventKey.isBlank()) {
            return false;
        }
        // 탈퇴/정지 등 비활성 회원은 알림 발송 대상이 아니므로 완결(스킵)로 처리하여 커서 블로킹 방지
        if (!memberNotificationQueryService.isMemberActive(receiverId)) {
            return true;
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
