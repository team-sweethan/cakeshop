package com.cakeshop.domain.statistics.aggregation;

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
        return aggregateDailyStatistics(false);
    }

    /** 시작 시 실행 잠금 획득 후에도 누락 날짜가 있을 때만 확정 통계를 집계한다. */
    public boolean catchUpMissingDailyStatistics() {
        return aggregateDailyStatistics(true);
    }

    private boolean aggregateDailyStatistics(boolean catchupOnly) {
        LocalDateTime sourceWindowEndedAt = batchRunMapper.findCurrentDateTime();
        LocalDate latestStatisticsDate = sourceWindowEndedAt.toLocalDate().minusDays(1);
        LocalDateTime sourceWindowStartedAt = findSourceWindowStartedAt();
        if (sourceWindowStartedAt == null) {
            log.warn("일별 통계 집계를 건너뜁니다. 성공한 초기 백필이 없습니다.");
            return false;
        }
        LocalDate latestSuccessfulTargetEndDate = batchRunMapper.findLatestSuccessfulTargetEndDate();
        if (latestSuccessfulTargetEndDate == null) {
            throw new IllegalStateException("마지막 성공 집계의 대상 종료일을 찾을 수 없습니다.");
        }

        List<LocalDate> targetDates = findTargetDates(
                sourceWindowStartedAt,
                sourceWindowEndedAt,
                latestSuccessfulTargetEndDate,
                latestStatisticsDate
        );
        StatisticsBatchRun batchRun = StatisticsBatchRun.daily(
                sourceWindowStartedAt,
                sourceWindowEndedAt,
                targetDates.getFirst(),
                targetDates.getLast()
        );

        Long batchRunId;
        try {
            if (catchupOnly) {
                batchRunId = transactionService.startDailyCatchupRun(
                        batchRun,
                        latestStatisticsDate
                );
            } else {
                batchRunId = transactionService.startRun(batchRun);
            }
        } catch (DuplicateKeyException exception) {
            log.info("일별 통계 집계를 건너뜁니다. 다른 집계가 실행 중입니다.");
            return false;
        }
        if (batchRunId == null) {
            log.info("시작 시 일별 통계 집계를 건너뜁니다. 어제까지 집계가 완료되어 있습니다.");
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
        if (batchRunMapper.findLatestSuccessfulBackfillStartedAt() == null) {
            return null;
        }
        return batchRunMapper.findEarliestBackfillStartedAt();
    }

    private List<LocalDate> findTargetDates(
            LocalDateTime sourceWindowStartedAt,
            LocalDateTime sourceWindowEndedAt,
            LocalDate latestSuccessfulTargetEndDate,
            LocalDate latestStatisticsDate
    ) {
        TreeSet<LocalDate> targetDates = new TreeSet<>(
                sourceReadModelMapper.findChangedStatisticsDates(
                        sourceWindowStartedAt,
                        sourceWindowEndedAt,
                        latestStatisticsDate
                )
        );
        if (latestSuccessfulTargetEndDate.isBefore(latestStatisticsDate)) {
            latestSuccessfulTargetEndDate.plusDays(1)
                    .datesUntil(latestStatisticsDate.plusDays(1))
                    .forEach(targetDates::add);
        }
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
