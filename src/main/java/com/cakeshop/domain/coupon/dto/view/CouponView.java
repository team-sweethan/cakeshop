package com.cakeshop.domain.coupon.dto.view;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 관리자 목록 화면에 필요한 조회 전용 값이다.
 * Entity를 화면에 직접 노출하지 않기 위해 목록 컬럼만 담는다.
 */
public record CouponView(
        Long id,
        String name,
        String discountType,
        BigDecimal discountValue,
        BigDecimal minimumOrderAmount,
        BigDecimal maximumDiscountAmount,
        Integer totalQuantity,
        Integer issuedQuantity,
        LocalDateTime startsAt,
        LocalDateTime expiresAt,
        String status
) {
}
