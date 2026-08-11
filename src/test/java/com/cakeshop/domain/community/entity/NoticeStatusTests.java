package com.cakeshop.domain.community.entity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

class NoticeStatusTests {

    @ParameterizedTest(name = "{0} -> {1} = {2}")
    @CsvSource({
            "PUBLISHED, DELETED,   true",

            "DELETED,   PUBLISHED, false",

            "PUBLISHED, PUBLISHED, false",
            "DELETED,   DELETED,   false"
    })
    void canTransitionTo_followsPolicy(NoticeStatus current, NoticeStatus next, boolean expected) {
        assertThat(current.canTransitionTo(next)).isEqualTo(expected);
    }

    @ParameterizedTest
    @EnumSource(NoticeStatus.class)
    void canTransitionTo_nullTarget_isRejected(NoticeStatus current) {
        assertThat(current.canTransitionTo(null)).isFalse();
    }
}
