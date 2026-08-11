package com.cakeshop.domain.coupon.dto.view;

import com.cakeshop.domain.coupon.entity.Coupon;
import com.cakeshop.domain.coupon.entity.CouponStatus;
import java.time.LocalDateTime;

/** DB 관리 상태, 발급 기간, 수량을 조합해 화면에 표시하는 쿠폰 상태다. */
public enum CouponDisplayStatus {
    SCHEDULED("발급 예정"),
    ACTIVE("발급 중"),
    INACTIVE("발급 중지"),
    EXHAUSTED("발급 완료"),
    ENDED("종료");

    private final String description;

    CouponDisplayStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    public static CouponDisplayStatus from(Coupon coupon, LocalDateTime now) {
        if (!coupon.getExpiresAt().isAfter(now)) {
            return ENDED;
        }
        if (coupon.getStatus() == CouponStatus.INACTIVE) {
            return INACTIVE;
        }
        if (coupon.getStartsAt().isAfter(now)) {
            return SCHEDULED;
        }
        if (coupon.getTotalQuantity() != null
                && coupon.getIssuedQuantity() >= coupon.getTotalQuantity()) {
            return EXHAUSTED;
        }
        return ACTIVE;
    }
}
