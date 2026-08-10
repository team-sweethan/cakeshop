package com.cakeshop.domain.review.entity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

class ReviewStatusTests {

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

    @ParameterizedTest
    @EnumSource(ReviewStatus.class)
    void canTransitionTo_nullTarget_isRejected(ReviewStatus current) {
        assertThat(current.canTransitionTo(null)).isFalse();
    }
}
