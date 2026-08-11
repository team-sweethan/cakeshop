package com.cakeshop.domain.coupon.dto.view;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

import com.cakeshop.domain.coupon.entity.Coupon;
import com.cakeshop.domain.coupon.entity.CouponStatus;

class CouponDisplayStatusTests {

    @Test
    void fromExpiredCouponReturnsEndedBeforeAdministrativeStatus() {
        Coupon coupon = coupon(CouponStatus.INACTIVE, 10, 0,
                LocalDateTime.now().minusSeconds(1), LocalDateTime.now().minusDays(1));

        assertThat(CouponDisplayStatus.from(coupon, LocalDateTime.now()))
                .isEqualTo(CouponDisplayStatus.ENDED);
    }

    @Test
    void fromExhaustedCouponReturnsExhaustedBeforeAdministrativeStatus() {
        Coupon coupon = coupon(CouponStatus.INACTIVE, 10, 10,
                LocalDateTime.now().plusDays(1), LocalDateTime.now().minusDays(1));

        assertThat(CouponDisplayStatus.from(coupon, LocalDateTime.now()))
                .isEqualTo(CouponDisplayStatus.EXHAUSTED);
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
