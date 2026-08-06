package com.cakeshop.domain.coupon.dto.view;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.cakeshop.domain.coupon.entity.CustomerCouponStatus;

/** 관리자 발급 회원 목록에 표시할 회원 정보와 회원 쿠폰 사용 상태다. */
public record CouponIssuedMemberView(Long memberId, String name, String email, String phone, LocalDate birthDate,
                                     CustomerCouponStatus status, LocalDateTime usedAt) {

    /** JSON 응답과 화면에서 enum 이름 대신 표시 문구를 사용할 수 있게 제공한다. */
    public String getStatusDescription() {
        return status.getDescription();
    }
}
