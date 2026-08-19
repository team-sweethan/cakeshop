package com.cakeshop.domain.coupon.dto.view;

import java.time.LocalDateTime;

/**
 * ******************************
 * 작성자 : 김민정
 * 담당자 : 이정후
 * 작성일 : 2026-08-19
 * 기능 : 쿠폰 발급 알림용 연동 뷰 DTO
 * 설명 : 알림 도메인에 쿠폰 발급 알림 생성을 위한 최소 정보(회원쿠폰ID, 회원ID, 쿠폰명, 발급일시)를 제공한다.
 * ******************************
 */
public record CouponNotificationView(
        Long memberCouponId,
        Long couponId,
        Long memberId,
        String couponName,
        LocalDateTime issuedAt
) {
}
