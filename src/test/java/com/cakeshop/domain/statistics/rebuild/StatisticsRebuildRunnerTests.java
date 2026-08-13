package com.cakeshop.domain.statistics.rebuild;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.cakeshop.domain.statistics.dto.StatisticsRebuildRequest;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mock.env.MockEnvironment;

class StatisticsRebuildRunnerTests {

    @Test
    void run_startAndEndDateProvided_executesRequestedRange() throws Exception {
        StatisticsRebuildService service = mock(StatisticsRebuildService.class);
        MockEnvironment environment = rebuildEnvironment()
                .withProperty("app.statistics.rebuild.start-date", "2026-08-01")
                .withProperty("app.statistics.rebuild.end-date", "2026-08-10");
        StatisticsRebuildRequest request = new StatisticsRebuildRequest(
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 10)
        );
        when(service.rebuild(request)).thenReturn(StatisticsRebuildResult.SUCCEEDED);
        StatisticsRebuildRunner runner = new StatisticsRebuildRunner(service, environment);

        runner.run(new DefaultApplicationArguments());

        verify(service).rebuild(request);
    }

    @Test
    void run_endDateMissing_passesDefaultableRequest() throws Exception {
        StatisticsRebuildService service = mock(StatisticsRebuildService.class);
        MockEnvironment environment = rebuildEnvironment()
                .withProperty("app.statistics.rebuild.start-date", "2026-08-01");
        StatisticsRebuildRequest request = new StatisticsRebuildRequest(
                LocalDate.of(2026, 8, 1),
                null
        );
        when(service.rebuild(request)).thenReturn(StatisticsRebuildResult.SUCCEEDED);
        StatisticsRebuildRunner runner = new StatisticsRebuildRunner(service, environment);

        runner.run(new DefaultApplicationArguments());

        verify(service).rebuild(request);
    }

    @Test
    void run_startDateMissing_rejectsCommand() {
        StatisticsRebuildService service = mock(StatisticsRebuildService.class);
        StatisticsRebuildRunner runner = new StatisticsRebuildRunner(
                service,
                rebuildEnvironment()
        );

        assertThatThrownBy(() -> runner.run(new DefaultApplicationArguments()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("start-date");
        verifyNoInteractions(service);
    }

    @Test
    void run_invalidDateFormat_rejectsCommand() {
        StatisticsRebuildService service = mock(StatisticsRebuildService.class);
        MockEnvironment environment = rebuildEnvironment()
                .withProperty("app.statistics.rebuild.start-date", "2026/08/01");
        StatisticsRebuildRunner runner = new StatisticsRebuildRunner(service, environment);

        assertThatThrownBy(() -> runner.run(new DefaultApplicationArguments()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("yyyy-MM-dd");
        verifyNoInteractions(service);
    }

    @Test
    void run_backfillAlsoEnabled_rejectsBothCommands() {
        StatisticsRebuildService service = mock(StatisticsRebuildService.class);
        MockEnvironment environment = rebuildEnvironment()
                .withProperty("app.statistics.backfill.enabled", "true")
                .withProperty("app.statistics.rebuild.start-date", "2026-08-01");
        StatisticsRebuildRunner runner = new StatisticsRebuildRunner(service, environment);

        assertThatThrownBy(() -> runner.run(new DefaultApplicationArguments()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("동시에");
        verifyNoInteractions(service);
    }

    @Test
    void run_initialBackfillMissing_returnsFailure() {
        StatisticsRebuildService service = mock(StatisticsRebuildService.class);
        MockEnvironment environment = rebuildEnvironment()
                .withProperty("app.statistics.rebuild.start-date", "2026-08-01");
        StatisticsRebuildRequest request = new StatisticsRebuildRequest(
                LocalDate.of(2026, 8, 1),
                null
        );
        when(service.rebuild(request))
                .thenReturn(StatisticsRebuildResult.INITIAL_BACKFILL_REQUIRED);
        StatisticsRebuildRunner runner = new StatisticsRebuildRunner(service, environment);

        assertThatThrownBy(() -> runner.run(new DefaultApplicationArguments()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("초기 통계 백필");
    }

    @Test
    void run_anotherAggregationRunning_requestsRetry() {
        StatisticsRebuildService service = mock(StatisticsRebuildService.class);
        MockEnvironment environment = rebuildEnvironment()
                .withProperty("app.statistics.rebuild.start-date", "2026-08-01");
        StatisticsRebuildRequest request = new StatisticsRebuildRequest(
                LocalDate.of(2026, 8, 1),
                null
        );
        when(service.rebuild(request)).thenReturn(StatisticsRebuildResult.SKIPPED_RUNNING);
        StatisticsRebuildRunner runner = new StatisticsRebuildRunner(service, environment);

        assertThatThrownBy(() -> runner.run(new DefaultApplicationArguments()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("재시도");
    }

    @Test
    void runner_enabledProperty_isExplicitlyRequired() {
        ConditionalOnProperty condition = StatisticsRebuildRunner.class
                .getAnnotation(ConditionalOnProperty.class);

        assertThat(condition).isNotNull();
        assertThat(condition.prefix()).isEqualTo("app.statistics.rebuild");
        assertThat(condition.name()).containsExactly("enabled");
        assertThat(condition.havingValue()).isEqualTo("true");
        assertThat(condition.matchIfMissing()).isFalse();
    }

    private MockEnvironment rebuildEnvironment() {
        return new MockEnvironment()
                .withProperty("app.statistics.rebuild.enabled", "true");
    }
}
