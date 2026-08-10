package com.cakeshop.domain.coupon.service;

import com.cakeshop.domain.coupon.entity.CouponTargetType;

/**
 * 쿠폰 등록 트랜잭션이 확정된 뒤 기존 회원 대상 자동 발급을 시작하기 위한 이벤트다.
 *
 * <p>발급 작업을 등록 트랜잭션에서 분리해 대량 대상의 행 잠금이 오래 유지되지 않게 한다.</p>
 */
public record CouponIssueRequestedEvent(Long couponId, CouponTargetType targetType) {
}
