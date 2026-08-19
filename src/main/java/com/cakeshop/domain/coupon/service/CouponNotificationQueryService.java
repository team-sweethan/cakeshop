package com.cakeshop.domain.coupon.service;

import com.cakeshop.domain.coupon.dto.view.CouponNotificationView;
import com.cakeshop.domain.coupon.mapper.CouponNotificationMapper;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * ******************************
 * 작성자 : 김민정
 * 담당자 : 이정후
 * 작성일 : 2026-08-19
 * 기능 : 알림 연동 전용 쿠폰 조회 서비스
 * 설명 : 알림 도메인에 사용 시작일이 도래한 발급 쿠폰 및 만료 임박(3일 이내) 유효 쿠폰 목록 조회 기능을 제공하는 공개 계약 Service이다.
 * ******************************
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CouponNotificationQueryService {

    private final CouponNotificationMapper couponNotificationMapper;

    /**
     * 특정 시각(since) 또는 복합 커서 이후에 발급/시작된 유효 쿠폰 목록을 조회한다.
     */
    public List<CouponNotificationView> findRecentlyIssuedOrStartedMemberCoupons(
            LocalDateTime since,
            LocalDateTime lastIssuedAt,
            Long lastMemberCouponId,
            int limit) {
        if (since == null && lastIssuedAt == null) {
            return List.of();
        }
        int fetchLimit = limit > 0 ? limit : 100;
        return couponNotificationMapper.findRecentlyIssuedOrStartedMemberCoupons(since, lastIssuedAt, lastMemberCouponId, fetchLimit);
    }

    /**
     * 지정된 일수(days) 이내에 만료 예정인 유효 쿠폰 목록을 복합 커서(lastExpiresAt, lastMemberCouponId)로 조회한다.
     */
    public List<CouponNotificationView> findExpiringMemberCoupons(
            int days,
            LocalDateTime lastExpiresAt,
            Long lastMemberCouponId,
            int limit) {
        int targetDays = days > 0 ? days : 3;
        int fetchLimit = limit > 0 ? limit : 100;
        return couponNotificationMapper.findExpiringMemberCoupons(targetDays, lastExpiresAt, lastMemberCouponId, fetchLimit);
    }

    /**
     * 발송 직전 해당 회원 쿠폰이 여전히 사용 가능(AVAILABLE)하고 활성(ACTIVE) 상태이며 만료되지 않았는지 재확인한다.
     */
    public boolean isMemberCouponAvailableAndUnexpired(Long memberCouponId, LocalDateTime expiresAt) {
        if (memberCouponId == null || expiresAt == null) {
            return false;
        }
        return couponNotificationMapper.isMemberCouponAvailableAndUnexpired(memberCouponId, expiresAt);
    }
}
