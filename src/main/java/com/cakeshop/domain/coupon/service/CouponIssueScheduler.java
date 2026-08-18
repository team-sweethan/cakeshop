package com.cakeshop.domain.coupon.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 생일 쿠폰 발급 작업을 주기적으로 실행한다.
 *
 * <p>작성자: 이정후 - CouponIssueService의 생일 대상 발급을 예약 실행한다.</p>
 */
@Component
@Slf4j
public class CouponIssueScheduler {

    private final CouponIssueService couponIssueService;

    public CouponIssueScheduler(CouponIssueService couponIssueService) {
        this.couponIssueService = couponIssueService;
    }

    /** 생일 쿠폰 발급을 서울 시간 기준 매시 정각에 실행한다. */
    @Scheduled(cron = "0 0 * * * *", zone = "Asia/Seoul")
    public void issueCoupons() {
        try {
            couponIssueService.issueBirthdayCoupons();
        } catch (RuntimeException exception) {
            log.error("생일 쿠폰 발급 스케줄러 실행에 실패했습니다.", exception);
            throw exception;
        }
    }
}
