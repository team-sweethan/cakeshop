package com.cakeshop.domain.statistics.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

class StatisticsBackfillRunnerTests {

    @Test
    void run_backfillSucceeded_completesCommand() throws Exception {
        InitialStatisticsBackfillService service = mock(InitialStatisticsBackfillService.class);
        when(service.backfillInitialStatistics()).thenReturn(StatisticsBackfillResult.SUCCEEDED);
        StatisticsBackfillRunner runner = new StatisticsBackfillRunner(service);

        runner.run(new DefaultApplicationArguments());

        verify(service).backfillInitialStatistics();
    }

    @Test
    void run_backfillAlreadyCompleted_completesCommand() throws Exception {
        InitialStatisticsBackfillService service = mock(InitialStatisticsBackfillService.class);
        when(service.backfillInitialStatistics())
                .thenReturn(StatisticsBackfillResult.ALREADY_COMPLETED);
        StatisticsBackfillRunner runner = new StatisticsBackfillRunner(service);

        runner.run(new DefaultApplicationArguments());

        verify(service).backfillInitialStatistics();
    }

    @Test
    void run_anotherAggregationRunning_requestsRetry() {
        InitialStatisticsBackfillService service = mock(InitialStatisticsBackfillService.class);
        when(service.backfillInitialStatistics())
                .thenReturn(StatisticsBackfillResult.SKIPPED_RUNNING);
        StatisticsBackfillRunner runner = new StatisticsBackfillRunner(service);

        assertThatThrownBy(() -> runner.run(new DefaultApplicationArguments()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("재시도");
    }

    @Test
    void runner_enabledProperty_isExplicitlyRequired() {
        ConditionalOnProperty condition = StatisticsBackfillRunner.class
                .getAnnotation(ConditionalOnProperty.class);

        assertThat(condition).isNotNull();
        assertThat(condition.prefix()).isEqualTo("app.statistics.backfill");
        assertThat(condition.name()).containsExactly("enabled");
        assertThat(condition.havingValue()).isEqualTo("true");
        assertThat(condition.matchIfMissing()).isFalse();
    }
}
