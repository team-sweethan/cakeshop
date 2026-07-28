package com.cakeshop.domain.coupon.dto.form;

import com.cakeshop.domain.coupon.entity.Coupon;

/**
 * 등록 Form의 검증 규칙을 재사용하면서 기존 쿠폰 값을 수정 화면에 채우기 위한 Form이다.
 */
public class CouponUpdateForm extends CouponCreateForm {

    /** 발급 수량과 상태처럼 수정 불가한 값은 제외하고 화면 입력값만 변환한다. */
    public static CouponUpdateForm from(Coupon coupon) {
        CouponUpdateForm form = new CouponUpdateForm();

        form.setName(coupon.getName());
        form.setDiscountType(coupon.getDiscountType());
        form.setDiscountValue(coupon.getDiscountValue());
        form.setMinimumOrderAmount(coupon.getMinimumOrderAmount());
        form.setMaximumDiscountAmount(coupon.getMaximumDiscountAmount());
        form.setTotalQuantity(coupon.getTotalQuantity().longValue());
        form.setStartsAt(coupon.getStartsAt());
        form.setExpiresAt(coupon.getExpiresAt());

        return form;
    }
}
