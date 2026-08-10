package com.cakeshop.domain.coupon.service;

import org.springframework.stereotype.Service;

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

    private final CouponIssueService couponIssueService;

    public CouponMemberCommandService(CouponIssueService couponIssueService) {
        this.couponIssueService = couponIssueService;
    }

    /** 회원가입 트랜잭션에 참여해 NEW_MEMBERS 대상 쿠폰을 발급한다. */
    public void issueNewMemberCoupons(long memberId) {
        couponIssueService.issueNewMemberCoupons(memberId);
    }
}
