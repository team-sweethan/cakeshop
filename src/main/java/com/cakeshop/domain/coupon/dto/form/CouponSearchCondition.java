package com.cakeshop.domain.coupon.dto.form;

import com.cakeshop.domain.coupon.dto.view.CouponDisplayStatus;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CouponSearchCondition {

    private String keyword;
    private CouponDisplayStatus status;

    public String normalizedKeyword() {
        // 공백만 입력한 검색은 검색 조건이 없는 것과 동일하게 처리한다.
        if (keyword == null || keyword.isBlank()) {
            return null;
        }

        return keyword.trim();
    }
}
