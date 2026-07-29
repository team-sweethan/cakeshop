package com.cakeshop.domain.coupon.entity;

/** MyBatis가 enum 이름을 DB의 discount_type VARCHAR 값으로 저장·조회한다. */
public enum DiscountType {
    // 금액 할인
    FIXED_AMOUNT,
    // 비율 할인
    PERCENTAGE
}
