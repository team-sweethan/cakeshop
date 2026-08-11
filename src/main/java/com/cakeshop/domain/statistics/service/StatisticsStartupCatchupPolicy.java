package com.cakeshop.domain.statistics.service;

import com.cakeshop.domain.statistics.mapper.StatisticsBatchRunMapper;
import java.time.LocalTime;
import org.springframework.stereotype.Component;

/** DB 현재 시각을 기준으로 시작 시 누락 통계를 따라잡을지 판단한다. */
@Component
public class StatisticsStartupCatchupPolicy {

    private final StatisticsBatchRunMapper batchRunMapper;

    public StatisticsStartupCatchupPolicy(StatisticsBatchRunMapper batchRunMapper) {
        this.batchRunMapper = batchRunMapper;
    }

    /** 정기 집계 시작 시각에 도달했으면 시작 시 따라잡기를 허용한다. */
    public boolean shouldCatchUp() {
        LocalTime databaseTime = batchRunMapper.findCurrentDateTime().toLocalTime();
        return !databaseTime.isBefore(DailyStatisticsAggregationSchedule.START_TIME);
    }
}
