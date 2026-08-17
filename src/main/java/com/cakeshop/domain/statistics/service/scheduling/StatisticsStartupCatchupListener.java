package com.cakeshop.domain.statistics.service.scheduling;

import com.cakeshop.domain.statistics.service.aggregation.DailyStatisticsAggregationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** 일반 애플리케이션 시작 후 누락된 일별 통계 따라잡기를 요청한다. */
@Component
@ConditionalOnProperty(
        prefix = "app.statistics",
        name = {"backfill.enabled", "rebuild.enabled"},
        havingValue = "false",
        matchIfMissing = true
)
public class StatisticsStartupCatchupListener {

    private static final Logger log = LoggerFactory.getLogger(StatisticsStartupCatchupListener.class);

    private final StatisticsStartupCatchupPolicy catchupPolicy;
    private final DailyStatisticsAggregationService aggregationService;

    public StatisticsStartupCatchupListener(
            StatisticsStartupCatchupPolicy catchupPolicy,
            DailyStatisticsAggregationService aggregationService
    ) {
        this.catchupPolicy = catchupPolicy;
        this.aggregationService = aggregationService;
    }

    /** 준비 완료 시각이 집계 기준을 지났으면 기존 일별 집계 로직으로 누락분을 처리한다. */
    @EventListener(ApplicationReadyEvent.class)
    public void catchUpMissingDailyStatistics() {
        try {
            if (!catchupPolicy.shouldCatchUp()) {
                return;
            }
            aggregationService.catchUpMissingDailyStatistics();
        } catch (RuntimeException exception) {
            log.error(
                    "애플리케이션 시작 시 누락된 일별 통계 따라잡기에 실패했습니다.",
                    exception
            );
        }
    }
}
