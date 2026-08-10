package com.cakeshop.domain.statistics.service;

import com.cakeshop.domain.statistics.entity.StatisticsBatchRun;
import com.cakeshop.domain.statistics.mapper.DailyStatisticsAggregationMapper;
import com.cakeshop.domain.statistics.mapper.StatisticsBatchRunMapper;
import java.time.LocalDate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 통계 집계의 독립 트랜잭션 경계를 제공한다. */
@Service
public class StatisticsAggregationTransactionService {

    private final DailyStatisticsAggregationMapper aggregationMapper;
    private final StatisticsBatchRunMapper batchRunMapper;

    public StatisticsAggregationTransactionService(
            DailyStatisticsAggregationMapper aggregationMapper,
            StatisticsBatchRunMapper batchRunMapper
    ) {
        this.aggregationMapper = aggregationMapper;
        this.batchRunMapper = batchRunMapper;
    }

    /** 만료 실행을 정리하고 새 일별 실행 잠금을 획득한다. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public long startDailyRun(StatisticsBatchRun batchRun) {
        batchRunMapper.failExpiredRunningBatch();
        batchRunMapper.insertRunningBatch(batchRun);
        if (batchRun.getId() == null) {
            throw new IllegalStateException("통계 집계 실행 식별자가 생성되지 않았습니다.");
        }
        return batchRun.getId();
    }

    /** 한 날짜의 집계 결과 교체와 heartbeat 갱신을 원자적으로 처리한다. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void replaceDate(long batchRunId, LocalDate statisticsDate) {
        aggregationMapper.replaceDailyStatistics(
                statisticsDate,
                statisticsDate.atStartOfDay(),
                statisticsDate.plusDays(1).atStartOfDay()
        );
        if (batchRunMapper.updateHeartbeat(batchRunId) != 1) {
            throw new IllegalStateException("실행 중인 통계 집계를 찾을 수 없습니다.");
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
}
