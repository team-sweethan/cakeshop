package com.cakeshop.domain.coupon.entity;

/** 쿠폰을 발급할 회원 집합과 발급 시점을 결정하는 정책이다. */
public enum CouponTargetType {
    ALL_MEMBERS("전체 회원"),
    NEW_MEMBERS("신규 회원"),
    FIRST_ORDER("첫 주문 회원"),
    BIRTHDAY("생일 회원"),
    SPECIFIC_MEMBERS("특정 회원");

    private final String description;

    CouponTargetType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
