package com.cakeshop.domain.community.entity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

class PostStatusTests {

    /** 게시글 상태 전이 규칙을 검증한다. */
    @ParameterizedTest(name = "{0} -> {1} = {2}")
    @CsvSource({
            "PUBLISHED, DELETED,   true",
            "PUBLISHED, BLOCKED,   true",
            "BLOCKED,   PUBLISHED, true",

            "BLOCKED,   DELETED,   false",
            "DELETED,   PUBLISHED, false",
            "DELETED,   BLOCKED,   false",

            // 동일 상태 전이는 거절한다.
            "PUBLISHED, PUBLISHED, false",
            "BLOCKED,   BLOCKED,   false",
            "DELETED,   DELETED,   false"
    })
    void canTransitionTo_followsPolicy(PostStatus current, PostStatus next, boolean expected) {
        assertThat(current.canTransitionTo(next)).isEqualTo(expected);
    }

    /** null 상태 전이를 거절한다. */
    @ParameterizedTest
    @EnumSource(PostStatus.class)
    void canTransitionTo_nullTarget_isRejected(PostStatus current) {
        assertThat(current.canTransitionTo(null)).isFalse();
    }
}
