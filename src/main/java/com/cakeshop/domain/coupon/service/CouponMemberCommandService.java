package com.cakeshop.domain.coupon.service;

import com.cakeshop.domain.coupon.entity.Coupon;
import com.cakeshop.domain.coupon.entity.CouponTargetType;
import com.cakeshop.domain.coupon.error.CouponErrorCode;
import com.cakeshop.domain.coupon.mapper.CouponMapper;
import com.cakeshop.global.error.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * ******************************
 * 작성자 : 이정후
 * 담당자 : 수민
 * 작성일 : 2026-08-10
 * 기능 : 신규 회원 쿠폰 발급 명령 제공
 * 설명 : member 도메인이 회원가입 완료 뒤 신규 회원 발급 정책을 실행할 때 호출한다.
 * ******************************
 */
@Service
public class CouponMemberCommandService {

    private final CouponMapper couponMapper;

    public CouponMemberCommandService(CouponMapper couponMapper) {
        this.couponMapper = couponMapper;
    }

    /** 회원가입 트랜잭션에 참여해 NEW_MEMBERS 대상 쿠폰을 발급한다. */
    @Transactional
    public void issueNewMemberCoupons(long memberId) {
        for (Coupon coupon : couponMapper.findCouponsByTargetType(CouponTargetType.NEW_MEMBERS)) {
            if (couponMapper.insertMemberCouponIfAbsent(coupon.getId(), memberId, false) == 1
                    && couponMapper.increaseIssuedQuantityIfAvailable(coupon.getId()) != 1) {
                throw new BusinessException(CouponErrorCode.UPDATE_FAILED);
            }
        }
    }
}
