package com.cakeshop.domain.review.entity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

class ReviewStatusTests {

    /**
     * 후기 상태 전이 규칙을 한 표로 고정한다(docs/review/DOMAIN.md 2.1).
     *
     * <p>표로 두는 편이 규칙을 통째로 읽기 쉽다. 규칙이 늘면 메서드가 아니라 줄을 추가한다.
     *
     * <p>{@code BLOCKED -> DELETED}가 거짓인 것이 특히 중요하다. 숨김은 관리자가 걸어 둔
     * 상태라 작성자가 지워서 없앨 수 없어야 한다. 그래서 <b>숨겨진 후기에 대해 작성자가 할 수
     * 있는 일은 없다</b> — 수정도 삭제도 막힌다.
     *
     * <p>다만 이 표는 <b>어느 전이가 허용되는지</b>만 정한다. 전이가 실제로 원자적으로
     * 일어나는지는 여기서 지켜지지 않는다 — 판단과 쓰기 사이에 상태가 바뀌면 금지된 전이가
     * 결과적으로 성립한다. 그쪽은 {@code UPDATE ... WHERE status = ?} 와 동시 요청 테스트가
     * 맡는다(docs/review/DOMAIN.md 2.1). 조각 4·5 차례다.
     */
    @ParameterizedTest(name = "{0} -> {1} = {2}")
    @CsvSource({
            "PUBLISHED, DELETED,   true",
            "PUBLISHED, BLOCKED,   true",
            "BLOCKED,   PUBLISHED, true",

            "BLOCKED,   DELETED,   false",
            "DELETED,   PUBLISHED, false",
            "DELETED,   BLOCKED,   false",

            // 같은 상태로의 전이는 변화가 없으므로 거절한다.
            "PUBLISHED, PUBLISHED, false",
            "BLOCKED,   BLOCKED,   false",
            "DELETED,   DELETED,   false"
    })
    void canTransitionTo_followsPolicy(ReviewStatus current, ReviewStatus next, boolean expected) {
        assertThat(current.canTransitionTo(next)).isEqualTo(expected);
    }

    /** 어떤 상태에서든 대상이 없으면 전이가 아니다. */
    @ParameterizedTest
    @EnumSource(ReviewStatus.class)
    void canTransitionTo_nullTarget_isRejected(ReviewStatus current) {
        assertThat(current.canTransitionTo(null)).isFalse();
    }
}
