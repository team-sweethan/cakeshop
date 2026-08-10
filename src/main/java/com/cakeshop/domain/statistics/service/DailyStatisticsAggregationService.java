package com.cakeshop.domain.statistics.service;

import com.cakeshop.domain.statistics.entity.StatisticsBatchRun;
import com.cakeshop.domain.statistics.mapper.DailyStatisticsSourceReadModelMapper;
import com.cakeshop.domain.statistics.mapper.StatisticsBatchRunMapper;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.TreeSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

@Service
public class DailyStatisticsAggregationService {

    private static final Logger log = LoggerFactory.getLogger(DailyStatisticsAggregationService.class);

    private final DailyStatisticsSourceReadModelMapper sourceReadModelMapper;
    private final StatisticsBatchRunMapper batchRunMapper;
    private final StatisticsAggregationTransactionService transactionService;

    public DailyStatisticsAggregationService(
            DailyStatisticsSourceReadModelMapper sourceReadModelMapper,
            StatisticsBatchRunMapper batchRunMapper,
            StatisticsAggregationTransactionService transactionService
    ) {
        this.sourceReadModelMapper = sourceReadModelMapper;
        this.batchRunMapper = batchRunMapper;
        this.transactionService = transactionService;
    }

    /** 전날과 원본 변경이 발견된 과거 날짜의 확정 통계를 집계한다. */
    public boolean aggregateDailyStatistics() {
        LocalDateTime sourceWindowEndedAt = batchRunMapper.findCurrentDateTime();
        LocalDate latestStatisticsDate = sourceWindowEndedAt.toLocalDate().minusDays(1);
        LocalDateTime sourceWindowStartedAt = findSourceWindowStartedAt();
        if (sourceWindowStartedAt == null) {
            log.warn("일별 통계 집계를 건너뜁니다. 성공한 초기 백필이 없습니다.");
            return false;
        }

        List<LocalDate> targetDates = findTargetDates(
                sourceWindowStartedAt,
                sourceWindowEndedAt,
                latestStatisticsDate
        );
        StatisticsBatchRun batchRun = StatisticsBatchRun.daily(
                sourceWindowStartedAt,
                sourceWindowEndedAt,
                targetDates.getFirst(),
                targetDates.getLast()
        );

        long batchRunId;
        try {
            batchRunId = transactionService.startRun(batchRun);
        } catch (DuplicateKeyException exception) {
            log.info("일별 통계 집계를 건너뜁니다. 다른 집계가 실행 중입니다.");
            return false;
        }

        try {
            for (LocalDate targetDate : targetDates) {
                transactionService.replaceDate(batchRunId, targetDate);
            }
            transactionService.completeSucceeded(batchRunId);
            log.info(
                    "일별 통계 집계를 완료했습니다. batchRunId={}, targetStartDate={}, targetEndDate={}",
                    batchRunId,
                    targetDates.getFirst(),
                    targetDates.getLast()
            );
            return true;
        } catch (RuntimeException exception) {
            markFailed(batchRunId, exception);
            throw exception;
        }
    }

    private LocalDateTime findSourceWindowStartedAt() {
        LocalDateTime latestDailyWindowEnd = batchRunMapper.findLatestSuccessfulDailyWindowEnd();
        if (latestDailyWindowEnd != null) {
            return latestDailyWindowEnd;
        }
        return batchRunMapper.findLatestSuccessfulBackfillStartedAt();
    }

    private List<LocalDate> findTargetDates(
            LocalDateTime sourceWindowStartedAt,
            LocalDateTime sourceWindowEndedAt,
            LocalDate latestStatisticsDate
    ) {
        TreeSet<LocalDate> targetDates = new TreeSet<>(
                sourceReadModelMapper.findChangedStatisticsDates(
                        sourceWindowStartedAt,
                        sourceWindowEndedAt,
                        latestStatisticsDate
                )
        );
        targetDates.add(latestStatisticsDate);
        return List.copyOf(targetDates);
    }

    private void markFailed(long batchRunId, RuntimeException originalException) {
        try {
            transactionService.completeFailed(batchRunId);
        } catch (RuntimeException completionException) {
            originalException.addSuppressed(completionException);
        }
        log.error("일별 통계 집계에 실패했습니다. batchRunId={}", batchRunId, originalException);
    }
}
