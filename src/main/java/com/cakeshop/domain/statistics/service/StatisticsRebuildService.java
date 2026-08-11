package com.cakeshop.domain.statistics.service;

import com.cakeshop.domain.statistics.dto.StatisticsRebuildRequest;
import com.cakeshop.domain.statistics.entity.StatisticsBatchRun;
import com.cakeshop.domain.statistics.mapper.StatisticsBatchRunMapper;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

@Service
public class StatisticsRebuildService {

    private static final Logger log = LoggerFactory.getLogger(StatisticsRebuildService.class);
    private static final long MAX_RANGE_DAYS = 366;

    private final StatisticsBatchRunMapper batchRunMapper;
    private final StatisticsAggregationTransactionService transactionService;

    public StatisticsRebuildService(
            StatisticsBatchRunMapper batchRunMapper,
            StatisticsAggregationTransactionService transactionService
    ) {
        this.batchRunMapper = batchRunMapper;
        this.transactionService = transactionService;
    }

    /** 지정 기간의 확정 통계를 날짜 오름차순으로 다시 집계한다. */
    public StatisticsRebuildResult rebuild(StatisticsRebuildRequest request) {
        LocalDate yesterday = batchRunMapper.findCurrentDateTime().toLocalDate().minusDays(1);
        LocalDate targetEndDate = request.endDate() == null ? yesterday : request.endDate();
        validateRange(request.startDate(), targetEndDate, yesterday);

        if (batchRunMapper.findLatestSuccessfulBackfillStartedAt() == null) {
            log.info("통계 재집계를 건너뜁니다. 성공한 초기 백필이 필요합니다.");
            return StatisticsRebuildResult.INITIAL_BACKFILL_REQUIRED;
        }

        StatisticsBatchRun batchRun = StatisticsBatchRun.rebuild(
                request.startDate(),
                targetEndDate
        );
        long batchRunId;
        try {
            batchRunId = transactionService.startRun(batchRun);
        } catch (DuplicateKeyException exception) {
            log.info("통계 재집계를 건너뜁니다. 다른 집계가 실행 중입니다.");
            return StatisticsRebuildResult.SKIPPED_RUNNING;
        }

        try {
            for (LocalDate targetDate = request.startDate();
                    !targetDate.isAfter(targetEndDate);
                    targetDate = targetDate.plusDays(1)) {
                transactionService.replaceRebuildDate(batchRunId, targetDate);
            }
            transactionService.completeSucceeded(batchRunId);
            log.info(
                    "통계 재집계를 완료했습니다. batchRunId={}, targetStartDate={}, targetEndDate={}",
                    batchRunId,
                    request.startDate(),
                    targetEndDate
            );
            return StatisticsRebuildResult.SUCCEEDED;
        } catch (RuntimeException exception) {
            markFailed(batchRunId, exception);
            throw exception;
        }
    }

    private void validateRange(
            LocalDate targetStartDate,
            LocalDate targetEndDate,
            LocalDate yesterday
    ) {
        if (targetStartDate.isAfter(targetEndDate)) {
            throw new IllegalArgumentException("재집계 시작일은 종료일보다 늦을 수 없습니다.");
        }
        if (targetEndDate.isAfter(yesterday)) {
            throw new IllegalArgumentException("재집계 종료일은 어제보다 늦을 수 없습니다.");
        }
        if (ChronoUnit.DAYS.between(targetStartDate, targetEndDate) >= MAX_RANGE_DAYS) {
            throw new IllegalArgumentException("재집계 기간은 최대 366일까지 지정할 수 있습니다.");
        }
    }

    private void markFailed(long batchRunId, RuntimeException originalException) {
        try {
            transactionService.completeFailed(batchRunId);
        } catch (RuntimeException completionException) {
            originalException.addSuppressed(completionException);
        }
        log.error("통계 재집계에 실패했습니다. batchRunId={}", batchRunId, originalException);
    }
}
