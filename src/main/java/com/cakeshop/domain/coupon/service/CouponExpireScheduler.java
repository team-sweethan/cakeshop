package com.cakeshop.domain.coupon.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class CouponExpireScheduler {

    private final CouponAdminService couponAdminService;

    /**
     * 10분마다 실행한다.
     * 발급 중지 상태라도 만료 시각이 지나면 ENDED로 확정한다.
     */
    @Scheduled(cron = "0 */10 * * * *", zone = "Asia/Seoul")
    public void endExpiredCoupons() {
        int updatedCount = couponAdminService.endExpiredCoupons();

        if (updatedCount > 0) {
            log.info("만료 쿠폰 상태 변경 완료: count={}", updatedCount);
        }
    }
}
