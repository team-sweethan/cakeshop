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
 * <p><b>좋아요 순은 넣지 않는다</b>(PLAN.md 조각 7 D8). 분기마다 tiebreaker·인덱스·형태
 * 검사가 함께 늘어나는데, 좋아요로 줄을 세워 보고 싶은 것은 인기글 점수가 이미 대신한다 —
 * 그 점수에서 가장 큰 계수를 갖는 것이 좋아요다.
 */
public enum PostSort {

    /** 최신순. 기본값이다. */
    LATEST("LATEST", "최신순"),

    /** 조회수 많은 순. */
    VIEWS("VIEWS", "조회수순");

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
