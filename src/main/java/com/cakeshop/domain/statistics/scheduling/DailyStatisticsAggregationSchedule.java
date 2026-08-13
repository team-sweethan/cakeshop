package com.cakeshop.domain.statistics.scheduling;

import java.time.LocalTime;

/** 정기 일별 집계와 시작 시 따라잡기가 공유하는 실행 시각 계약이다. */
final class DailyStatisticsAggregationSchedule {

    static final String CRON = "0 10 0 * * *";
    static final String TIME_ZONE = "Asia/Seoul";
    static final LocalTime START_TIME = LocalTime.of(0, 10);

    private DailyStatisticsAggregationSchedule() {
    }
}
