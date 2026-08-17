package com.cakeshop.domain.statistics.service.scheduling;

import com.cakeshop.domain.statistics.mapper.StatisticsBatchRunMapper;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.springframework.stereotype.Component;

/** DB 현재 시각을 기준으로 시작 시 누락 통계를 따라잡을지 판단한다. */
@Component
public class StatisticsStartupCatchupPolicy {

    private final StatisticsBatchRunMapper batchRunMapper;

    public StatisticsStartupCatchupPolicy(StatisticsBatchRunMapper batchRunMapper) {
        this.batchRunMapper = batchRunMapper;
    }

    /** 정기 집계 시작 시각 이후이고 어제까지 누락된 날짜가 있으면 따라잡기를 허용한다. */
    public boolean shouldCatchUp() {
        LocalDateTime databaseDateTime = batchRunMapper.findCurrentDateTime();
        if (databaseDateTime.toLocalTime().isBefore(DailyStatisticsAggregationSchedule.START_TIME)) {
            return false;
        }

        LocalDate latestSuccessfulDate = batchRunMapper.findLatestSuccessfulTargetEndDate();
        LocalDate yesterday = databaseDateTime.toLocalDate().minusDays(1);
        return latestSuccessfulDate != null && latestSuccessfulDate.isBefore(yesterday);
    }
}
