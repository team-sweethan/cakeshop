package com.cakeshop.domain.community.dto.view;

/**
 * 고객 목록의 정렬 기준(docs/community/DOMAIN.md 6.1).
 *
 * 주소에서 들어오는 문자열을 그대로 SQL에 잇지 않기 위한 타입이다. 허용값만 이 enum이
 * 되고, SQL에서는 {@code <choose>} 분기로 갈린다 — {@code ${}} 로 이으면 정렬 문자열이
 * 그대로 쿼리가 된다(AGENTS.md). 관리자 목록의 {@link AdminPostSort}와 같은 방식이다.
 *
 * <p>모르는 값을 오류로 만들지 않고 기본값으로 떨어뜨리는 것은 카테고리·페이지 파라미터와
 * 같은 처리다. 목록은 공개 화면이라 주소가 망가졌다고 오류 페이지를 줄 이유가 없다.
 *
 * <p><b>좋아요순은 D8이 기각했다가 2026-08-18에 열었다</b>(조각 16). 기각 근거였던
 * "인기글 점수가 대신한다"는 인기글이 7일 창 스냅샷이라 역대 좋아요 많은 글을 대신하지
 * 못하고, B1이 적어 둔 되돌아올 계기가 조각 15(사이드바 이동·메인 제거)로 실현됐다 —
 * 근거는 specs/community-read.md B1.
 */
public enum PostSort {

    /** 최신순. 기본값이다. */
    LATEST("LATEST", "최신순"),

    /** 조회수 많은 순. */
    VIEWS("VIEWS", "조회수순"),

    /** 좋아요 많은 순. */
    LIKES("LIKES", "좋아요순");

    private final String parameter;
    private final String label;

    PostSort(String parameter, String label) {
        this.parameter = parameter;
        this.label = label;
    }

    public String getParameter() {
        return parameter;
    }

    public String getLabel() {
        return label;
    }

    /** 주소에서 받은 값을 정렬 기준으로 바꾼다. 모르는 값과 빈 값은 기본값이다. */
    public static PostSort from(String value) {
        if (value == null) {
            return LATEST;
        }

        for (PostSort sort : values()) {
            if (sort.name().equalsIgnoreCase(value.trim())) {
                return sort;
            }
        }

        return LATEST;
    }
}
