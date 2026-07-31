package com.cakeshop.domain.coupon.dto.form;

import com.cakeshop.domain.coupon.entity.Coupon;
import com.cakeshop.domain.coupon.entity.CouponDisplayStatus;
import lombok.Getter;
import lombok.Setter;

/**
 * 등록 Form의 검증 규칙을 재사용하면서 기존 쿠폰 값을 수정 화면에 채우기 위한 Form이다.
 */
@Getter
@Setter
public class CouponUpdateForm extends CouponCreateForm {

    /** 상세/수정 화면에서 종료 쿠폰의 수정 버튼을 숨기는 화면 계산 상태다. */
    private CouponDisplayStatus displayStatus;

    /** 시작 전이면 전체 수정, 시작 후면 제한 수정 화면을 렌더링하기 위한 서버 계산값이다. */
    private boolean fullEdit;

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
