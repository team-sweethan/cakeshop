package com.cakeshop.domain.notification.service;

import com.cakeshop.domain.member.service.MemberNotificationQueryService;
import com.cakeshop.domain.notification.entity.NotificationType;
import com.cakeshop.domain.notification.mapper.NotificationMapper;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * ******************************
 * 작성자 : 김민정
 * 담당자 : 김민정
 * 작성일 : 2026-08-19
 * 기능 : 쿠폰 알림 전용 멱등성 및 상태 확인 서비스
 * 설명 : 쿠폰 만료 임박 알림이 이미 성공적으로 완료되었는지 또는 수신 회원이 비활성 상태인지 검증한다. (SMS 재시도 허용 및 만료일 연장 지원)
 * ******************************
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationCouponQueryService {

    private static final DateTimeFormatter EXPIRE_KEY_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmm");

    private final NotificationMapper notificationMapper;
    private final MemberNotificationQueryService memberNotificationQueryService;

    /**
     * 특정 회원에게 해당 쿠폰 만료 임박 알림이 이미 성공적으로 완료(SENT/DELIVERED 또는 2회 상한 도달)되었거나 비활성 회원인지 검사한다.
     * (SMS 발송 실패 건으로 재시도가 필요한 경우 false를 반환하여 재발송 분기로 전달)
     */
    public boolean isCouponExpiringNotificationCompletedOrInactive(Long memberId, Long memberCouponId, LocalDateTime expiresAt) {
        if (memberId == null || memberCouponId == null || expiresAt == null) {
            return true;
        }

        // 비활성(탈퇴/정지) 회원이면 알림 발송 불필요하므로 완료 처리
        if (!memberNotificationQueryService.isMemberActive(memberId)) {
            return true;
        }

        String expireKey = EXPIRE_KEY_FORMAT.format(expiresAt);
        String eventKey = NotificationType.COUPON_EXPIRING_SOON.name() + ":" + memberId + ":" + memberCouponId + ":" + expireKey;
        Long notificationId = notificationMapper.findIdByReceiverIdAndEventKey(memberId, eventKey);
        if (notificationId == null) {
            return false;
        }

        // 이미 외부 SMS가 성공(SENT/DELIVERED)했거나 2회 상한에 도달했으면 완료 처리
        if (notificationMapper.hasSentDelivery(notificationId)) {
            return true;
        }
        return notificationMapper.countDeliveryAttempts(notificationId) >= 2;
    }
}
