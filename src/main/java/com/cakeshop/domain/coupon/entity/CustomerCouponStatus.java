package com.cakeshop.domain.coupon.entity;

/** 회원에게 발급된 쿠폰의 사용 상태를 DB 문자열과 동일하게 표현한다. */
public enum CustomerCouponStatus {
    AVAILABLE("사용 가능"),
    RESERVED("결제 대기"),
    USED("사용 완료"),
    ENDED("만료");

    private final String description;

    CustomerCouponStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
