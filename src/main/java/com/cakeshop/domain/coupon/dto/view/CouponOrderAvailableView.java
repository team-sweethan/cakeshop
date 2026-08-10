package com.cakeshop.domain.coupon.dto.view;

/** 주문서 선택 목록에 표시할 사용 가능한 회원 쿠폰이다. */
public record CouponOrderAvailableView(Long memberCouponId, String name) {
}
