package com.cakeshop.domain.statistics.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** DB의 statistics_batch_runs 한 행을 표현한다. */
@Getter
@Setter
@NoArgsConstructor
public class StatisticsBatchRun {

    private Long id;
    private StatisticsBatchType batchType;
    private StatisticsBatchStatus status;
    private LocalDateTime sourceWindowStartedAt;
    private LocalDateTime sourceWindowEndedAt;
    private LocalDate targetStartDate;
    private LocalDate targetEndDate;
    private LocalDate lastCompletedDate;
    private LocalDateTime startedAt;
    private LocalDateTime heartbeatAt;
    private LocalDateTime completedAt;

    public static StatisticsBatchRun daily(
            LocalDateTime sourceWindowStartedAt,
            LocalDateTime sourceWindowEndedAt,
            LocalDate targetStartDate,
            LocalDate targetEndDate
    ) {
        StatisticsBatchRun run = new StatisticsBatchRun();
        run.batchType = StatisticsBatchType.DAILY;
        run.sourceWindowStartedAt = sourceWindowStartedAt;
        run.sourceWindowEndedAt = sourceWindowEndedAt;
        run.targetStartDate = targetStartDate;
        run.targetEndDate = targetEndDate;
        return run;
    }

    public static StatisticsBatchRun backfill(
            LocalDate targetStartDate,
            LocalDate targetEndDate
    ) {
        StatisticsBatchRun run = new StatisticsBatchRun();
        run.batchType = StatisticsBatchType.BACKFILL;
        run.targetStartDate = targetStartDate;
        run.targetEndDate = targetEndDate;
        return run;
    }
}
