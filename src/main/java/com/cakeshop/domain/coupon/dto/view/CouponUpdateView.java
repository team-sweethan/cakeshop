package com.cakeshop.domain.coupon.dto.view;

import com.cakeshop.domain.coupon.dto.form.CouponUpdateForm;
import com.cakeshop.domain.coupon.entity.Coupon;
import com.cakeshop.domain.coupon.entity.CouponTargetType;
import com.cakeshop.domain.coupon.entity.DiscountType;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 관리자 수정 화면을 렌더링하는 읽기 전용 모델이다. */
public record CouponUpdateView(
        String name,
        DiscountType discountType,
        BigDecimal discountValue,
        BigDecimal minimumOrderAmount,
        BigDecimal maximumDiscountAmount,
        Long totalQuantity,
        LocalDateTime startsAt,
        LocalDateTime expiresAt,
        CouponTargetType targetType,
        CouponDisplayStatus displayStatus,
        boolean fullEdit
) {

    public static CouponUpdateView from(
            Coupon coupon, CouponDisplayStatus displayStatus, boolean fullEdit
    ) {
        return new CouponUpdateView(
                coupon.getName(), coupon.getDiscountType(), coupon.getDiscountValue(),
                coupon.getMinimumOrderAmount(), coupon.getMaximumDiscountAmount(),
                coupon.getTotalQuantity() == null ? null : coupon.getTotalQuantity().longValue(),
                coupon.getStartsAt(), coupon.getExpiresAt(), coupon.getTargetType(), displayStatus, fullEdit
        );
    }

    /** 수정 Form은 HTTP 요청 바인딩과 입력 검증에만 사용한다. */
    public CouponUpdateForm toForm() {
        CouponUpdateForm form = new CouponUpdateForm();
        form.setName(name);
        form.setDiscountType(discountType);
        form.setDiscountValue(discountValue);
        form.setMinimumOrderAmount(minimumOrderAmount);
        form.setMaximumDiscountAmount(maximumDiscountAmount);
        form.setTotalQuantity(totalQuantity);
        form.setStartsAt(startsAt);
        form.setExpiresAt(expiresAt);
        return form;
    }
}
