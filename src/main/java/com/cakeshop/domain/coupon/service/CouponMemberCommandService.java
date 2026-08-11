package com.cakeshop.domain.coupon.service;

import com.cakeshop.domain.coupon.error.CouponErrorCode;
import com.cakeshop.domain.coupon.mapper.CouponMemberMapper;
import com.cakeshop.global.error.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * ******************************
 * 작성자 : 수민(이정후)
 * 담당자 : 이정후
 * 작성일 : 2026-08-10
 * 기능 : 신규 회원 쿠폰 발급 명령 제공
 * 설명 : member 도메인이 회원가입 완료 뒤 신규 회원 발급 정책을 실행할 때 호출한다.
 * ******************************
 */
@Service
public class CouponMemberCommandService {

    private final CouponMemberMapper couponMemberMapper;

    public CouponMemberCommandService(CouponMemberMapper couponMemberMapper) {
        this.couponMemberMapper = couponMemberMapper;
    }

    /** 회원가입 트랜잭션에 참여해 NEW_MEMBERS 대상 쿠폰을 발급한다. */
    @Transactional
    public void issueNewMemberCoupons(long memberId) {
        for (Long couponId : couponMemberMapper.findAvailableNewMemberCouponIds()) {
            if (couponMemberMapper.insertNewMemberCouponIfAbsent(couponId, memberId) == 1
                    && couponMemberMapper.increaseNewMemberCouponIssuedQuantity(couponId) != 1) {
                throw new BusinessException(CouponErrorCode.UPDATE_FAILED);
            }
        }
    }
}
