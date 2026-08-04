package com.cakeshop.domain.community.entity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

class ReportStatusTests {

    /**
     * 신고 상태 전이 규칙을 한 표로 고정한다(DOMAIN.md 6.6). PostStatusTests와 같은 형태다.
     *
     * <p>처리된 신고에서 나가는 전이가 전부 거짓인 것이 중요하다. 신고 상태는 "그때
     * 조치했다"는 기록이므로, 차단을 해제해도 RESOLVED가 PENDING으로 돌아가지 않는다.
     */
    @ParameterizedTest(name = "{0} -> {1} = {2}")
    @CsvSource({
            "PENDING,  RESOLVED, true",
            "PENDING,  REJECTED, true",

            "RESOLVED, PENDING,  false",
            "RESOLVED, REJECTED, false",
            "REJECTED, PENDING,  false",
            "REJECTED, RESOLVED, false",

            // 같은 상태로의 전이는 변화가 없으므로 거절한다.
            "PENDING,  PENDING,  false",
            "RESOLVED, RESOLVED, false",
            "REJECTED, REJECTED, false"
    })
    void canTransitionTo_followsPolicy(
            ReportStatus current, ReportStatus next, boolean expected) {
        assertThat(current.canTransitionTo(next)).isEqualTo(expected);
    }

    /** 어떤 상태에서든 대상이 없으면 전이가 아니다. */
    @ParameterizedTest
    @EnumSource(ReportStatus.class)
    void canTransitionTo_nullTarget_isRejected(ReportStatus current) {
        assertThat(current.canTransitionTo(null)).isFalse();
    }
}
