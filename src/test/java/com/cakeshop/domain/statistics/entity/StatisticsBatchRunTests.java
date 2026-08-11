package com.cakeshop.domain.statistics.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class StatisticsBatchRunTests {

    @Test
    void rebuild_createsRebuildRunForRequestedRange() {
        LocalDate startDate = LocalDate.of(2026, 8, 1);
        LocalDate endDate = LocalDate.of(2026, 8, 10);

        StatisticsBatchRun run = StatisticsBatchRun.rebuild(startDate, endDate);

        assertThat(run.getBatchType()).isEqualTo(StatisticsBatchType.REBUILD);
        assertThat(run.getTargetStartDate()).isEqualTo(startDate);
        assertThat(run.getTargetEndDate()).isEqualTo(endDate);
        assertThat(run.getSourceWindowStartedAt()).isNull();
        assertThat(run.getSourceWindowEndedAt()).isNull();
        assertThat(run.getLastCompletedDate()).isNull();
    }
}
