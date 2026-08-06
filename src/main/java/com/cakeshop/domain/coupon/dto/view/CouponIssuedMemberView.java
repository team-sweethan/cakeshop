package com.cakeshop.domain.coupon.dto.view;

import java.time.LocalDateTime;

import com.cakeshop.domain.coupon.entity.CustomerCouponStatus;

/** 관리자 발급 회원 목록에 표시할 회원 정보와 회원 쿠폰 사용 상태다. */
public record CouponIssuedMemberView(Long memberId, String name, String email, String phone, String birthday,
                                     CustomerCouponStatus status, LocalDateTime usedAt) {

    /** JSON 응답에는 원본 연락처 대신 화면 표시용으로 마스킹한 값을 담는다. */
    public CouponIssuedMemberView {
        phone = maskPhone(phone);
    }

    /** JSON 응답과 화면에서 enum 이름 대신 표시 문구를 사용할 수 있게 제공한다. */
    public String getStatusDescription() {
        return status.getDescription();
    }

    private static String maskPhone(String phone) {
        if (phone == null || phone.isBlank()) {
            return null;
        }

        String digits = phone.replaceAll("\\D", "");
        if (digits.length() < 8) {
            return "****";
        }
        return digits.substring(0, 3) + "-****-" + digits.substring(digits.length() - 4);
    }
}
