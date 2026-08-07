package com.cakeshop.domain.member.dto.view;

import java.time.LocalDateTime;

/**
 * ******************************
 * 작성자 : 이정후
 * 담당자 : 수민
 * 작성일 : 2026-08-07
 * 기능 : 쿠폰 발급 회원 이력 조회 결과
 * 설명 : 쿠폰 관리자 화면이 발급 이력과 회원 프로필을 페이지 단위로 함께 조회할 때 사용한다.
 * ******************************
 */
public record MemberCouponIssuedHistoryView(
        Long memberId,
        String name,
        String email,
        String phone,
        String birthday,
        String couponStatus,
        LocalDateTime usedAt
) {
}
