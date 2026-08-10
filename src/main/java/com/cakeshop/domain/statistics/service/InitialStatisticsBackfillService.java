package com.cakeshop.domain.statistics.service;

import com.cakeshop.domain.statistics.entity.StatisticsBatchRun;
import com.cakeshop.domain.statistics.mapper.DailyStatisticsAggregationMapper;
import com.cakeshop.domain.statistics.mapper.StatisticsBatchRunMapper;
import java.time.LocalDate;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

@Service
public class InitialStatisticsBackfillService {

    private static final Logger log = LoggerFactory.getLogger(InitialStatisticsBackfillService.class);

    private final DailyStatisticsAggregationMapper aggregationMapper;
    private final StatisticsBatchRunMapper batchRunMapper;
    private final StatisticsAggregationTransactionService transactionService;

    public InitialStatisticsBackfillService(
            DailyStatisticsAggregationMapper aggregationMapper,
            StatisticsBatchRunMapper batchRunMapper,
            StatisticsAggregationTransactionService transactionService
    ) {
        this.aggregationMapper = aggregationMapper;
        this.batchRunMapper = batchRunMapper;
        this.transactionService = transactionService;
    }

    /** 최초 통계 범위를 날짜 오름차순으로 집계하고 중단된 진행 지점부터 재개한다. */
    public boolean backfillInitialStatistics() {
        if (batchRunMapper.findLatestSuccessfulBackfillStartedAt() != null) {
            log.info("초기 통계 백필을 건너뜁니다. 성공한 백필이 이미 존재합니다.");
            return false;
        }

        LocalDate targetEndDate = batchRunMapper.findCurrentDateTime().toLocalDate().minusDays(1);
        LocalDate initialStartDate = findInitialStartDate(targetEndDate);
        LocalDate targetStartDate = findResumeStartDate(initialStartDate, targetEndDate);
        StatisticsBatchRun batchRun = StatisticsBatchRun.backfill(targetStartDate, targetEndDate);

        Long batchRunId;
        try {
            batchRunId = transactionService.startBackfillRun(batchRun);
        } catch (DuplicateKeyException exception) {
            log.info("초기 통계 백필을 건너뜁니다. 다른 집계가 실행 중입니다.");
            return false;
        }
        if (batchRunId == null) {
            log.info("초기 통계 백필을 건너뜁니다. 성공한 백필이 이미 존재합니다.");
            return false;
        }

        try {
            for (LocalDate targetDate = targetStartDate;
                    !targetDate.isAfter(targetEndDate);
                    targetDate = targetDate.plusDays(1)) {
                transactionService.replaceBackfillDate(batchRunId, targetDate);
            }
            transactionService.completeSucceeded(batchRunId);
            log.info(
                    "초기 통계 백필을 완료했습니다. batchRunId={}, targetStartDate={}, targetEndDate={}",
                    batchRunId,
                    targetStartDate,
                    targetEndDate
            );
            return true;
        } catch (RuntimeException exception) {
            markFailed(batchRunId, exception);
            throw exception;
        }
    }

    private LocalDate findInitialStartDate(LocalDate targetEndDate) {
        return Stream.of(
                        aggregationMapper.findEarliestOrderDate(),
                        aggregationMapper.findEarliestApprovedPaymentDate(),
                        targetEndDate.minusDays(6)
                )
                .filter(date -> date != null)
                .min(LocalDate::compareTo)
                .orElseThrow();
    }

    private LocalDate findResumeStartDate(
            LocalDate initialStartDate,
            LocalDate targetEndDate
    ) {
        LocalDate lastCompletedDate = batchRunMapper.findLatestBackfillCompletedDate();
        if (lastCompletedDate == null) {
            return initialStartDate;
        }

        LocalDate nextDate = lastCompletedDate.plusDays(1);
        if (nextDate.isBefore(initialStartDate)) {
            return initialStartDate;
        }
        if (nextDate.isAfter(targetEndDate)) {
            return targetEndDate;
        }
        return nextDate;
    }

    private void markFailed(long batchRunId, RuntimeException originalException) {
        try {
            transactionService.completeFailed(batchRunId);
        } catch (RuntimeException completionException) {
            originalException.addSuppressed(completionException);
        }
        log.error("초기 통계 백필에 실패했습니다. batchRunId={}", batchRunId, originalException);
    }
}
