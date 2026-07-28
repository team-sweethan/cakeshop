package com.cakeshop.domain.coupon.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** 고객용 쿠폰 화면의 진입 요청을 처리한다. */
@Controller
public class CouponController {

    /**
     * 회원 쿠폰 발급/사용 기능이 구현되기 전까지 고객용 쿠폰 화면의 진입 경로만 제공한다.
     * 실제 회원 보유 쿠폰 조회는 2차 member_coupons 기능에서 추가한다.
     */
    @GetMapping("/mypage/coupons")
    public String list() {
        return "customer/coupon/list";
    }
}
