package com.cakeshop.domain.notification.service;

import com.cakeshop.domain.member.service.MemberNotificationQueryService;
import com.cakeshop.domain.notification.entity.NotificationType;
import com.cakeshop.domain.notification.mapper.NotificationMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * ******************************
 * 작성자 : 김민정
 * 담당자 : 김민정
 * 작성일 : 2026-08-19
 * 기능 : 쿠폰 알림 전용 멱등성 및 상태 확인 서비스
 * 설명 : 쿠폰 만료 임박 알림이 이미 전송되었는지 또는 수신 회원이 비활성 상태인지 검증한다.
 * ******************************
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationCouponQueryService {

    private final NotificationMapper notificationMapper;
    private final MemberNotificationQueryService memberNotificationQueryService;

    /**
     * 특정 회원에게 해당 쿠폰 만료 임박 알림이 이미 전송되었거나 비활성 회원인지 검사한다.
     * (전송되었거나 비활성이면 true 반환하여 발송을 스킵)
     */
    public boolean isCouponExpiringNotificationSentOrInactive(Long memberId, Long memberCouponId) {
        if (memberId == null || memberCouponId == null) {
            return true;
        }

        // 비활성(탈퇴/정지) 회원이면 알림 발송 불필요하므로 true 반환
        if (!memberNotificationQueryService.isMemberActive(memberId)) {
            return true;
        }

        String eventKey = NotificationType.COUPON_EXPIRING_SOON.name() + ":" + memberId + ":" + memberCouponId;
        Long notificationId = notificationMapper.findIdByReceiverIdAndEventKey(memberId, eventKey);
        if (notificationId == null) {
            return false;
        }

        // 이미 성공(SENT/DELIVERED) 발송 이력이 있거나, 시도 상한(2회)에 도달했으면 완료 처리
        if (notificationMapper.hasSentDelivery(notificationId)) {
            return true;
        }
        return notificationMapper.countDeliveryAttempts(notificationId) >= 2;
    }
}
