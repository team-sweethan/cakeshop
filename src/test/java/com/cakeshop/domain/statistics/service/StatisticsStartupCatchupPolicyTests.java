package com.cakeshop.domain.statistics.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cakeshop.domain.statistics.mapper.StatisticsBatchRunMapper;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class StatisticsStartupCatchupPolicyTests {

    private static final LocalDate CURRENT_DATE = LocalDate.of(2026, 8, 11);

    @ParameterizedTest
    @CsvSource({
            "00:09:59, -1, false",
            "00:10:00, -1, true",
            "23:59:59, -1, true",
            "00:10:00, 0, false",
            "12:00:00, 1, false"
    })
    void shouldCatchUp_databaseTimeAndLatestDate_appliesMissingDateBoundary(
            LocalTime databaseTime,
            int latestDateOffsetFromYesterday,
            boolean expected
    ) {
        StatisticsBatchRunMapper batchRunMapper = mock(StatisticsBatchRunMapper.class);
        LocalDateTime databaseDateTime = LocalDateTime.of(CURRENT_DATE, databaseTime);
        LocalDate yesterday = CURRENT_DATE.minusDays(1);
        when(batchRunMapper.findCurrentDateTime()).thenReturn(databaseDateTime);
        when(batchRunMapper.findLatestSuccessfulTargetEndDate()).thenReturn(
                yesterday.plusDays(latestDateOffsetFromYesterday)
        );
        StatisticsStartupCatchupPolicy policy =
                new StatisticsStartupCatchupPolicy(batchRunMapper);

        boolean actual = policy.shouldCatchUp();

        assertThat(actual).isEqualTo(expected);
        if (databaseTime.isBefore(DailyStatisticsAggregationSchedule.START_TIME)) {
            verify(batchRunMapper, never()).findLatestSuccessfulTargetEndDate();
        }
    }

    @Test
    void shouldCatchUp_noSuccessfulAggregation_rejectsCatchup() {
        StatisticsBatchRunMapper batchRunMapper = mock(StatisticsBatchRunMapper.class);
        when(batchRunMapper.findCurrentDateTime()).thenReturn(
                LocalDateTime.of(CURRENT_DATE, LocalTime.NOON)
        );
        when(batchRunMapper.findLatestSuccessfulTargetEndDate()).thenReturn(null);
        StatisticsStartupCatchupPolicy policy =
                new StatisticsStartupCatchupPolicy(batchRunMapper);

        boolean actual = policy.shouldCatchUp();

        assertThat(actual).isFalse();
    }
}
