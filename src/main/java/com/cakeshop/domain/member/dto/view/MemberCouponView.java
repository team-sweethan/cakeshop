package com.cakeshop.domain.member.dto.view;

import java.time.LocalDate;

/** 쿠폰 발급 대상과 발급 이력 화면에 제공하는 회원 공개 조회 데이터다. */
public record MemberCouponView(
        Long memberId,
        String name,
        String email,
        String phone,
        LocalDate birthDate
) {
}
