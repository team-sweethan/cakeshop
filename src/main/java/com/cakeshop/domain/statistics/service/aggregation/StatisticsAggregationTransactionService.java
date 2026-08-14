package com.cakeshop.domain.statistics.service.aggregation;

import com.cakeshop.domain.statistics.dto.source.DailyAdditionalMetricsSourceView;
import com.cakeshop.domain.statistics.dto.source.DailyProductStatisticsSourceView;
import com.cakeshop.domain.statistics.entity.StatisticsBatchRun;
import com.cakeshop.domain.statistics.mapper.DailyStatisticsAggregationMapper;
import com.cakeshop.domain.statistics.mapper.DailyStatisticsSourceReadModelMapper;
import com.cakeshop.domain.statistics.mapper.StatisticsBatchRunMapper;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 통계 집계의 독립 트랜잭션 경계를 제공한다. */
@Service
public class StatisticsAggregationTransactionService {

    private final DailyStatisticsAggregationMapper aggregationMapper;
    private final DailyStatisticsSourceReadModelMapper sourceReadModelMapper;
    private final DailyProductStatisticsSourceReadModelQueryService productSourceQueryService;
    private final StatisticsBatchRunMapper batchRunMapper;

    public StatisticsAggregationTransactionService(
            DailyStatisticsAggregationMapper aggregationMapper,
            DailyStatisticsSourceReadModelMapper sourceReadModelMapper,
            DailyProductStatisticsSourceReadModelQueryService productSourceQueryService,
            StatisticsBatchRunMapper batchRunMapper
    ) {
        this.aggregationMapper = aggregationMapper;
        this.sourceReadModelMapper = sourceReadModelMapper;
        this.productSourceQueryService = productSourceQueryService;
        this.batchRunMapper = batchRunMapper;
    }

    /** 만료 실행을 정리하고 새 집계 실행 잠금을 획득한다. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public long startRun(StatisticsBatchRun batchRun) {
        batchRunMapper.failExpiredRunningBatch();
        batchRunMapper.insertRunningBatch(batchRun);
        return requireBatchRunId(batchRun);
    }

    /** 실행 잠금 획득 후 누락 여부를 다시 확인하고 시작 시 일별 집계를 등록한다. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Long startDailyCatchupRun(
            StatisticsBatchRun batchRun,
            LocalDate latestStatisticsDate
    ) {
        batchRunMapper.failExpiredRunningBatch();
        batchRunMapper.insertRunningBatch(batchRun);
        long batchRunId = requireBatchRunId(batchRun);

        LocalDate latestSuccessfulDate = batchRunMapper.findLatestSuccessfulTargetEndDate();
        if (latestSuccessfulDate != null && !latestSuccessfulDate.isBefore(latestStatisticsDate)) {
            if (batchRunMapper.deleteRunningDailyBatch(batchRunId) != 1) {
                throw new IllegalStateException("취소할 시작 시 통계 집계를 찾을 수 없습니다.");
            }
            return null;
        }
        return batchRunId;
    }

    /** 성공 백필을 다시 확인한 뒤 새 백필 실행 잠금을 획득한다. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Long startBackfillRun(StatisticsBatchRun batchRun) {
        batchRunMapper.failExpiredRunningBatch();
        if (batchRunMapper.findLatestSuccessfulBackfillStartedAt() != null) {
            return null;
        }
        batchRunMapper.insertRunningBatch(batchRun);
        return requireBatchRunId(batchRun);
    }

    /** 한 날짜의 집계 결과 교체와 heartbeat 갱신을 원자적으로 처리한다. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void replaceDate(long batchRunId, LocalDate statisticsDate) {
        replaceStatisticsDate(statisticsDate);
        if (batchRunMapper.updateHeartbeat(batchRunId) != 1) {
            throw new IllegalStateException("실행 중인 통계 집계를 찾을 수 없습니다.");
        }
    }

    /** 한 날짜의 백필 결과 교체와 진행일 갱신을 원자적으로 처리한다. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void replaceBackfillDate(long batchRunId, LocalDate statisticsDate) {
        replaceStatisticsDate(statisticsDate);
        if (batchRunMapper.updateBackfillProgress(batchRunId, statisticsDate) != 1) {
            throw new IllegalStateException("실행 중인 통계 백필을 찾을 수 없습니다.");
        }
    }

    /** 한 날짜의 재집계 결과 교체와 진행일 갱신을 원자적으로 처리한다. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void replaceRebuildDate(long batchRunId, LocalDate statisticsDate) {
        replaceStatisticsDate(statisticsDate);
        if (batchRunMapper.updateRebuildProgress(batchRunId, statisticsDate) != 1) {
            throw new IllegalStateException("실행 중인 통계 재집계를 찾을 수 없습니다.");
        }
    }

    /** 실행 중인 집계를 성공 처리한다. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void completeSucceeded(long batchRunId) {
        if (batchRunMapper.completeSucceeded(batchRunId) != 1) {
            throw new IllegalStateException("성공 처리할 통계 집계를 찾을 수 없습니다.");
        }
    }

    /** 실행 중인 집계를 실패 처리한다. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void completeFailed(long batchRunId) {
        if (batchRunMapper.completeFailed(batchRunId) != 1) {
            throw new IllegalStateException("실패 처리할 통계 집계를 찾을 수 없습니다.");
        }
    }

    private long requireBatchRunId(StatisticsBatchRun batchRun) {
        if (batchRun.getId() == null) {
            throw new IllegalStateException("통계 집계 실행 식별자가 생성되지 않았습니다.");
        }
        return batchRun.getId();
    }

    private void replaceStatisticsDate(LocalDate statisticsDate) {
        LocalDateTime start = statisticsDate.atStartOfDay();
        LocalDateTime end = statisticsDate.plusDays(1).atStartOfDay();
        List<DailyProductStatisticsSourceView> productSources =
                productSourceQueryService.getDailyProductStatistics(start, end);
        DailyAdditionalMetricsSourceView additionalMetricsSource =
                sourceReadModelMapper.findDailyAdditionalMetrics(start, end);

        aggregationMapper.upsertDailyStatistics(
                statisticsDate,
                sourceReadModelMapper.findDailyStatistics(start, end)
        );
        if (aggregationMapper.updateDailyAdditionalMetrics(
                statisticsDate,
                additionalMetricsSource
        ) != 1) {
            throw new IllegalStateException("활동·금액 지표 집계 결과를 저장하지 못했습니다.");
        }
        aggregationMapper.deleteDailyProductStatistics(statisticsDate);
        if (!productSources.isEmpty()) {
            int insertedRows = aggregationMapper.insertDailyProductStatistics(
                    statisticsDate,
                    productSources
            );
            if (insertedRows != productSources.size()) {
                throw new IllegalStateException("상품별 통계 집계 결과를 모두 저장하지 못했습니다.");
            }
        }
        if (aggregationMapper.updateProductAggregatedAt(statisticsDate) != 1) {
            throw new IllegalStateException("상품별 통계 집계 완료 시각을 기록하지 못했습니다.");
        }
    }
}
