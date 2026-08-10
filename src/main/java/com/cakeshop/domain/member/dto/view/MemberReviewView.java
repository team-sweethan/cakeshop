package com.cakeshop.domain.member.dto.view;

/**
 * ******************************
 * 작성자 : HyunGyu-Cho
 * 담당자 : 수민
 * 작성일 : 2026-08-10
 * 기능 : 후기 작성자 표기용 회원 조회 계약
 * 설명 : 리뷰가 members 를 직접 JOIN 하지 않도록 작성자 표기에 필요한 최소 필드만 제공한다.
 * ******************************
 */
public record MemberReviewView(
        Long id,
        String nickname,
        boolean withdrawn
) {
}
