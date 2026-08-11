package com.cakeshop.domain.coupon.dto.form;

import com.cakeshop.domain.coupon.dto.view.CouponDisplayStatus;
import com.cakeshop.domain.coupon.entity.CouponTargetType;
import com.cakeshop.domain.coupon.entity.DiscountType;
import java.time.LocalDateTime;
import org.springframework.format.annotation.DateTimeFormat;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CouponSearchCondition {

    private String keyword;
    private CouponDisplayStatus status;
    // 쿠폰 등록·수정 Form의 discountType과 이름 충돌 없이 목록 검색에만 바인딩한다.
    private DiscountType discountTypeFilter;
    // 등록 Form의 targetType과 이름 충돌 없이 목록 검색에만 바인딩한다.
    private CouponTargetType targetTypeFilter;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime periodStart;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime periodEnd;

    public String normalizedKeyword() {
        // 공백만 입력한 검색은 검색 조건이 없는 것과 동일하게 처리한다.
        if (keyword == null || keyword.isBlank()) {
            return null;
        }

        return keyword.trim();
    }
}
