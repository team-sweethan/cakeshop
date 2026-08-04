package com.cakeshop.domain.community.entity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

class CommentStatusTests {

    /**
     * 댓글 상태 전이 규칙을 한 표로 고정한다.
     *
     * <p>댓글에는 차단이 없다. 지우는 것만 되고, 지운 댓글은 되돌릴 수 없다.
     */
    @ParameterizedTest(name = "{0} -> {1} = {2}")
    @CsvSource({
            "PUBLISHED, DELETED,   true",

            "DELETED,   PUBLISHED, false",

            // 같은 상태로의 전이는 변화가 없으므로 거절한다.
            "PUBLISHED, PUBLISHED, false",
            "DELETED,   DELETED,   false"
    })
    void canTransitionTo_followsPolicy(
            CommentStatus current, CommentStatus next, boolean expected) {
        assertThat(current.canTransitionTo(next)).isEqualTo(expected);
    }

    /** 어떤 상태에서든 대상이 없으면 전이가 아니다. */
    @ParameterizedTest
    @EnumSource(CommentStatus.class)
    void canTransitionTo_nullTarget_isRejected(CommentStatus current) {
        assertThat(current.canTransitionTo(null)).isFalse();
    }
}
