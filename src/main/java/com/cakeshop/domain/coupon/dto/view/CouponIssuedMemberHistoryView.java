package com.cakeshop.domain.coupon.dto.view;

import java.time.LocalDateTime;

import com.cakeshop.domain.coupon.entity.CustomerCouponStatus;

/** 쿠폰 도메인이 보유한 발급 이력과 회원 프로필을 조합하기 전의 조회 결과다. */
public record CouponIssuedMemberHistoryView(
        Long memberId,
        CustomerCouponStatus status,
        LocalDateTime issuedAt,
        LocalDateTime usedAt
) {
}
