package com.cakeshop.domain.coupon.entity;

/**
 * 쿠폰 캠페인의 관리 상태다.
 * ENDED는 스케줄러가 만료 시각을 기준으로 확정하며 관리자 재개 대상이 아니다.
 */
public enum CouponStatus {
    ACTIVE,     // 활성
    INACTIVE,   // 비활성
    ENDED       // 종료
}
