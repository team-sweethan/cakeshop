package com.cakeshop.domain.statistics.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class StatisticsRebuildRequestTests {

    @Test
    void create_startAndEndDate_preservesRequestedRange() {
        LocalDate startDate = LocalDate.of(2026, 8, 1);
        LocalDate endDate = LocalDate.of(2026, 8, 10);

        StatisticsRebuildRequest request = new StatisticsRebuildRequest(startDate, endDate);

        assertThat(request.startDate()).isEqualTo(startDate);
        assertThat(request.endDate()).isEqualTo(endDate);
    }

    @Test
    void create_noEndDate_acceptsDefaultableRequest() {
        StatisticsRebuildRequest request = new StatisticsRebuildRequest(
                LocalDate.of(2026, 8, 1),
                null
        );

        assertThat(request.endDate()).isNull();
    }

    @Test
    void create_noStartDate_rejectsRequest() {
        assertThatNullPointerException()
                .isThrownBy(() -> new StatisticsRebuildRequest(null, null))
                .withMessage("재집계 시작일은 필수입니다.");
    }
}
