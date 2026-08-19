package com.cakeshop.domain.coupon.dto.view;

import java.time.LocalDateTime;

/**
 * ******************************
 * 작성자 : 김민정
 * 담당자 : 이정후
 * 작성일 : 2026-08-19
 * 기능 : 쿠폰 알림용 연동 뷰 DTO
 * 설명 : 알림 도메인에 쿠폰 발급 및 만료 임박 알림 생성을 위한 최소 정보를 제공한다.
 * ******************************
 */
public record CouponNotificationView(
        Long memberCouponId,
        Long couponId,
        Long memberId,
        String couponName,
        LocalDateTime issuedAt,
        LocalDateTime startsAt,
        LocalDateTime expiresAt
) {
    /**
     * 발급 시각과 시작 시각 중 더 늦은 시점(실제 알림 이벤트 발생 유효 시점)을 반환한다.
     */
    public LocalDateTime effectiveAt() {
        if (issuedAt == null) return startsAt;
        if (startsAt == null) return issuedAt;
        return issuedAt.isAfter(startsAt) ? issuedAt : startsAt;
    }
}
