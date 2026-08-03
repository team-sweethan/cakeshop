package com.cakeshop.domain.community.entity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

class PostStatusTests {

    /**
     * 게시글 상태 전이 규칙을 한 표로 고정한다.
     *
     * <p>표로 두는 편이 규칙을 통째로 읽기 쉽다. 규칙이 늘면 메서드가 아니라 줄을 추가한다.
     *
     * <p>{@code BLOCKED -> DELETED}가 거짓인 것이 특히 중요하다. 차단된 글은 신고·조치의
     * 증거라서 작성자가 지워서 없앨 수 없어야 한다.
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
    void canTransitionTo_followsPolicy(PostStatus current, PostStatus next, boolean expected) {
        assertThat(current.canTransitionTo(next)).isEqualTo(expected);
    }

    /** 어떤 상태에서든 대상이 없으면 전이가 아니다. */
    @ParameterizedTest
    @EnumSource(PostStatus.class)
    void canTransitionTo_nullTarget_isRejected(PostStatus current) {
        assertThat(current.canTransitionTo(null)).isFalse();
    }
}
