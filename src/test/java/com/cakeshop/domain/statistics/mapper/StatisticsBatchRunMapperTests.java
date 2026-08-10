package com.cakeshop.domain.statistics.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cakeshop.domain.statistics.entity.StatisticsBatchRun;
import com.cakeshop.global.config.MariaDbIntegrationTest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

@MybatisTest
@MariaDbIntegrationTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class StatisticsBatchRunMapperTests {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final LocalDate TARGET_DATE = LocalDate.of(2026, 8, 9);
    private static final LocalDateTime WINDOW_START = LocalDateTime.of(2026, 8, 9, 0, 10);
    private static final LocalDateTime WINDOW_END = LocalDateTime.of(2026, 8, 10, 0, 10);

    private final StatisticsBatchRunMapper mapper;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    StatisticsBatchRunMapperTests(
            StatisticsBatchRunMapper mapper,
            JdbcTemplate jdbcTemplate
    ) {
        this.mapper = mapper;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Test
    void insertRunningBatch_runningExists_rejectsSecondRun() {
        StatisticsBatchRun dailyRun = newDailyRun(WINDOW_START, WINDOW_END);
        mapper.insertRunningBatch(dailyRun);

        assertThat(dailyRun.getId()).isPositive();
        assertThatThrownBy(() -> mapper.insertRunningBatch(newBackfillRun()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void failExpiredRunningBatch_heartbeatOlderThanOneHour_marksRunFailed() {
        StatisticsBatchRun run = newDailyRun(WINDOW_START, WINDOW_END);
        mapper.insertRunningBatch(run);
        jdbcTemplate.update(
                """
                UPDATE statistics_batch_runs
                SET heartbeat_at = CURRENT_TIMESTAMP(6) - INTERVAL 2 HOUR
                WHERE id = ?
                """,
                run.getId()
        );

        int updated = mapper.failExpiredRunningBatch();

        assertThat(updated).isOne();
        BatchRunState state = findState(run.getId());
        assertThat(state.status()).isEqualTo("FAILED");
        assertThat(state.completedAt()).isNotNull();
    }

    @Test
    void failExpiredRunningBatch_recentHeartbeat_keepsRunRunning() {
        StatisticsBatchRun run = newDailyRun(WINDOW_START, WINDOW_END);
        mapper.insertRunningBatch(run);

        int updated = mapper.failExpiredRunningBatch();

        assertThat(updated).isZero();
        assertThat(findState(run.getId()).status()).isEqualTo("RUNNING");
    }

    @Test
    void updateHeartbeat_runningRun_updatesHeartbeatOnlyWhileRunning() {
        StatisticsBatchRun run = newDailyRun(WINDOW_START, WINDOW_END);
        mapper.insertRunningBatch(run);
        jdbcTemplate.update(
                """
                UPDATE statistics_batch_runs
                SET heartbeat_at = CURRENT_TIMESTAMP(6) - INTERVAL 2 HOUR
                WHERE id = ?
                """,
                run.getId()
        );
        LocalDateTime oldHeartbeat = findHeartbeat(run.getId());

        assertThat(mapper.updateHeartbeat(run.getId())).isOne();
        assertThat(findHeartbeat(run.getId())).isAfter(oldHeartbeat);
        assertThat(mapper.completeSucceeded(run.getId())).isOne();
        assertThat(mapper.updateHeartbeat(run.getId())).isZero();
    }

    @Test
    void updateBackfillProgress_multipleRuns_returnsLatestSavedDate() {
        StatisticsBatchRun firstRun = newBackfillRun();
        mapper.insertRunningBatch(firstRun);
        assertThat(mapper.updateBackfillProgress(firstRun.getId(), TARGET_DATE.minusDays(3))).isOne();
        assertThat(mapper.completeFailed(firstRun.getId())).isOne();

        StatisticsBatchRun secondRun = newBackfillRun();
        mapper.insertRunningBatch(secondRun);
        assertThat(mapper.updateBackfillProgress(secondRun.getId(), TARGET_DATE.minusDays(2))).isOne();

        assertThat(mapper.findLatestBackfillCompletedDate()).isEqualTo(TARGET_DATE.minusDays(2));
    }

    @Test
    void findLatestSuccessfulBackfillStartedAt_failedNewerRun_returnsLatestSuccessStart() {
        StatisticsBatchRun successfulRun = newBackfillRun();
        mapper.insertRunningBatch(successfulRun);
        assertThat(mapper.completeSucceeded(successfulRun.getId())).isOne();
        LocalDateTime successfulStartedAt = jdbcTemplate.queryForObject(
                "SELECT started_at FROM statistics_batch_runs WHERE id = ?",
                LocalDateTime.class,
                successfulRun.getId()
        );

        StatisticsBatchRun failedRun = newBackfillRun();
        mapper.insertRunningBatch(failedRun);
        assertThat(mapper.completeFailed(failedRun.getId())).isOne();

        assertThat(mapper.findLatestSuccessfulBackfillStartedAt()).isEqualTo(successfulStartedAt);
    }

    @Test
    void findLatestSuccessfulDailyWindowEnd_failedNewerRun_returnsLatestSuccessWindowEnd() {
        StatisticsBatchRun successfulRun = newDailyRun(WINDOW_START, WINDOW_END);
        mapper.insertRunningBatch(successfulRun);
        assertThat(mapper.completeSucceeded(successfulRun.getId())).isOne();

        StatisticsBatchRun failedRun = newDailyRun(WINDOW_END, WINDOW_END.plusDays(1));
        mapper.insertRunningBatch(failedRun);
        assertThat(mapper.completeFailed(failedRun.getId())).isOne();

        assertThat(mapper.findLatestSuccessfulDailyWindowEnd()).isEqualTo(WINDOW_END);
    }

    @Test
    void findLatestSuccessfulTargetEndDate_failedNewerRun_returnsLatestSuccessTargetEnd() {
        StatisticsBatchRun successfulRun = newBackfillRun();
        mapper.insertRunningBatch(successfulRun);
        assertThat(mapper.completeSucceeded(successfulRun.getId())).isOne();

        StatisticsBatchRun failedRun = StatisticsBatchRun.daily(
                WINDOW_START,
                WINDOW_END,
                TARGET_DATE.plusDays(1),
                TARGET_DATE.plusDays(1)
        );
        mapper.insertRunningBatch(failedRun);
        assertThat(mapper.completeFailed(failedRun.getId())).isOne();

        assertThat(mapper.findLatestSuccessfulTargetEndDate()).isEqualTo(TARGET_DATE);
    }

    @Test
    void findCurrentDateTime_returnsDatabaseCurrentTime() {
        LocalDateTime before = LocalDateTime.now(SEOUL).minusSeconds(5);

        LocalDateTime databaseNow = mapper.findCurrentDateTime();

        assertThat(databaseNow).isAfter(before).isBefore(LocalDateTime.now(SEOUL).plusSeconds(5));
    }

    private StatisticsBatchRun newDailyRun(
            LocalDateTime sourceWindowStartedAt,
            LocalDateTime sourceWindowEndedAt
    ) {
        return StatisticsBatchRun.daily(
                sourceWindowStartedAt,
                sourceWindowEndedAt,
                TARGET_DATE,
                TARGET_DATE
        );
    }

    private StatisticsBatchRun newBackfillRun() {
        return StatisticsBatchRun.backfill(TARGET_DATE.minusDays(6), TARGET_DATE);
    }

    private BatchRunState findState(long batchRunId) {
        return jdbcTemplate.queryForObject(
                """
                SELECT status, completed_at
                FROM statistics_batch_runs
                WHERE id = ?
                """,
                (resultSet, rowNum) -> new BatchRunState(
                        resultSet.getString("status"),
                        resultSet.getObject("completed_at", LocalDateTime.class)
                ),
                batchRunId
        );
    }

    private LocalDateTime findHeartbeat(long batchRunId) {
        return jdbcTemplate.queryForObject(
                "SELECT heartbeat_at FROM statistics_batch_runs WHERE id = ?",
                LocalDateTime.class,
                batchRunId
        );
    }

    private record BatchRunState(String status, LocalDateTime completedAt) {
    }
}
