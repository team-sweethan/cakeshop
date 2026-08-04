package com.cakeshop.domain.community.dto.view;

/**
 * 관리자 목록의 정렬 기준(docs/community/DOMAIN.md 6.7).
 *
 * 주소에서 들어오는 문자열을 그대로 SQL에 잇지 않기 위한 타입이다. 허용값만 이 enum이
 * 되고, SQL에서는 {@code <choose>} 분기로 갈린다 — {@code ${}} 로 이으면 정렬 문자열이
 * 그대로 쿼리가 된다(AGENTS.md).
 *
 * 모르는 값을 오류로 만들지 않고 기본값으로 떨어뜨리는 것은 고객 목록의 카테고리·페이지
 * 파라미터와 같은 처리다. 주소가 망가졌다고 관리자에게 오류 페이지를 줄 이유가 없다.
 */
public enum AdminPostSort {

    /** 최신순. 기본값이다. */
    LATEST,

    /** 미처리 신고가 많은 순. 관리자가 조치할 글을 먼저 보여 준다. */
    REPORTS;

    /** 주소에서 받은 값을 정렬 기준으로 바꾼다. 모르는 값과 빈 값은 기본값이다. */
    public static AdminPostSort from(String value) {
        if (value == null) {
            return LATEST;
        }

        for (AdminPostSort sort : values()) {
            if (sort.name().equalsIgnoreCase(value.trim())) {
                return sort;
            }
        }

        return LATEST;
    }
}
