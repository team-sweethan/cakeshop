package com.cakeshop.domain.statistics.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.cakeshop.domain.statistics.mapper.StatisticsBatchRunMapper;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class StatisticsStartupCatchupPolicyTests {

    private static final LocalDate CURRENT_DATE = LocalDate.of(2026, 8, 11);

    @ParameterizedTest
    @CsvSource({
            "00:09:59, false",
            "00:10:00, true",
            "23:59:59, true"
    })
    void shouldCatchUp_databaseTime_appliesDailyAggregationBoundary(
            LocalTime databaseTime,
            boolean expected
    ) {
        StatisticsBatchRunMapper batchRunMapper = mock(StatisticsBatchRunMapper.class);
        LocalDateTime databaseDateTime = LocalDateTime.of(CURRENT_DATE, databaseTime);
        when(batchRunMapper.findCurrentDateTime()).thenReturn(databaseDateTime);
        StatisticsStartupCatchupPolicy policy =
                new StatisticsStartupCatchupPolicy(batchRunMapper);

        boolean actual = policy.shouldCatchUp();

        assertThat(actual).isEqualTo(expected);
    }
}
