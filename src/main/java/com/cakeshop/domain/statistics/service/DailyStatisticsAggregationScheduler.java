package com.cakeshop.domain.statistics.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 매일 확정 통계의 일별 집계를 시작한다. */
@Component
public class DailyStatisticsAggregationScheduler {

    static final String DAILY_AT_00_10 = "0 10 0 * * *";
    static final String SEOUL_TIME_ZONE = "Asia/Seoul";

    private final DailyStatisticsAggregationService aggregationService;

    public DailyStatisticsAggregationScheduler(
            DailyStatisticsAggregationService aggregationService
    ) {
        this.aggregationService = aggregationService;
    }

    /** 매일 00:10에 전날과 변경된 과거 날짜의 통계를 집계한다. */
    @Scheduled(cron = DAILY_AT_00_10, zone = SEOUL_TIME_ZONE)
    public void aggregateDailyStatistics() {
        aggregationService.aggregateDailyStatistics();
    }
}
