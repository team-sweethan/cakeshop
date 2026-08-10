package com.cakeshop.domain.member.dto.form;

import com.cakeshop.global.error.BusinessException;
import com.cakeshop.global.error.CommonErrorCode;

/**
 * ******************************
 * 작성자 : 이정후
 * 담당자 : 수민
 * 작성일 : 2026-08-10
 * 기능 : 쿠폰 발급 대상 회원 검색 기준
 * 설명 : 빈 값 전체 조회를 막고, 검색 기준별 최소 입력 길이를 검증한다.
 * ******************************
 */
public enum MemberCouponSearchType {
    MEMBER_ID(1),
    NAME(2),
    EMAIL(2),
    PHONE(4);

    private final int minimumLength;

    MemberCouponSearchType(int minimumLength) {
        this.minimumLength = minimumLength;
    }

    public static MemberCouponSearchType from(String value) {
        try {
            return value == null ? NAME : MemberCouponSearchType.valueOf(value);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }
    }

    public void validate(String keyword) {
        if (keyword == null || keyword.trim().length() < minimumLength) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }
        if (this == MEMBER_ID && !keyword.trim().matches("\\d+")) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }
    }
}
