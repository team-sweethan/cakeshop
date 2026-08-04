package com.cakeshop.domain.coupon.entity;

/** 관리자만 변경하는 쿠폰 발급 허용 상태다. */
public enum CouponStatus {
    ACTIVE,     // 관리자가 발급 허용
    INACTIVE    // 관리자가 발급 중지
}
