package com.cakeshop.domain.coupon.dto.view;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

import com.cakeshop.domain.coupon.entity.Coupon;
import com.cakeshop.domain.coupon.entity.CouponStatus;

class CouponDisplayStatusTests {

    @Test
    void fromExpiredCompletedCoupon_returnsEnded() {
        LocalDateTime now = LocalDateTime.now();
        Coupon coupon = coupon(CouponStatus.ACTIVE, 10, 10,
                now.minusSeconds(1), now.minusDays(1));

        assertThat(CouponDisplayStatus.from(coupon, now))
                .isEqualTo(CouponDisplayStatus.ENDED);
    }

    @Test
    void fromScheduledCompletedCoupon_returnsScheduled() {
        LocalDateTime now = LocalDateTime.now();
        Coupon coupon = coupon(CouponStatus.ACTIVE, 10, 10,
                now.plusDays(1), now.plusHours(1));

        assertThat(CouponDisplayStatus.from(coupon, now))
                .isEqualTo(CouponDisplayStatus.SCHEDULED);
    }

    @Test
    void fromActiveCompletedCoupon_returnsExhausted() {
        LocalDateTime now = LocalDateTime.now();
        Coupon coupon = coupon(CouponStatus.ACTIVE, 10, 10,
                now.plusDays(1), now.minusDays(1));

        assertThat(CouponDisplayStatus.from(coupon, now))
                .isEqualTo(CouponDisplayStatus.EXHAUSTED);
    }

    @Test
    void fromInactiveCompletedCoupon_returnsInactive() {
        LocalDateTime now = LocalDateTime.now();
        Coupon coupon = coupon(CouponStatus.INACTIVE, 10, 10,
                now.plusDays(1), now.minusDays(1));

        assertThat(CouponDisplayStatus.from(coupon, now))
                .isEqualTo(CouponDisplayStatus.INACTIVE);
    }

    @Test
    void fromFutureActiveCouponReturnsScheduled() {
        Coupon coupon = coupon(CouponStatus.ACTIVE, 10, 0,
                LocalDateTime.now().plusDays(1), LocalDateTime.now().plusHours(1));

        assertThat(CouponDisplayStatus.from(coupon, LocalDateTime.now()))
                .isEqualTo(CouponDisplayStatus.SCHEDULED);
    }

    @Test
    void fromUnlimitedCouponDoesNotReturnExhausted() {
        Coupon coupon = coupon(CouponStatus.ACTIVE, null, 100,
                LocalDateTime.now().plusDays(1), LocalDateTime.now().minusHours(1));

        assertThat(CouponDisplayStatus.from(coupon, LocalDateTime.now()))
                .isEqualTo(CouponDisplayStatus.ACTIVE);
    }

    private Coupon coupon(
            CouponStatus status,
            Integer totalQuantity,
            int issuedQuantity,
            LocalDateTime expiresAt,
            LocalDateTime startsAt) {
        Coupon coupon = new Coupon();
        coupon.setStatus(status);
        coupon.setTotalQuantity(totalQuantity);
        coupon.setIssuedQuantity(issuedQuantity);
        coupon.setStartsAt(startsAt);
        coupon.setExpiresAt(expiresAt);
        return coupon;
    }
}
