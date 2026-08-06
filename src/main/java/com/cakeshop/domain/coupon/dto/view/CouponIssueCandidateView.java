package com.cakeshop.domain.coupon.dto.view;

/** 특정 회원 발급 화면에서 회원 공개 정보와 쿠폰 발급 여부를 조합한 View다. */
public record CouponIssueCandidateView(
        Long memberId,
        String name,
        String email,
        String phone,
        String birthday,
        boolean issued
) {
}
