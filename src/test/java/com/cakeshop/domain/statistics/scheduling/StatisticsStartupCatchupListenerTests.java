package com.cakeshop.domain.statistics.scheduling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.cakeshop.domain.statistics.aggregation.DailyStatisticsAggregationService;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;

class StatisticsStartupCatchupListenerTests {

    @Test
    void catchUpMissingDailyStatistics_beforeDailyStart_skipsAggregation() {
        StatisticsStartupCatchupPolicy policy = mock(StatisticsStartupCatchupPolicy.class);
        DailyStatisticsAggregationService service = mock(DailyStatisticsAggregationService.class);
        when(policy.shouldCatchUp()).thenReturn(false);
        StatisticsStartupCatchupListener listener =
                new StatisticsStartupCatchupListener(policy, service);

        listener.catchUpMissingDailyStatistics();

        verify(policy).shouldCatchUp();
        verifyNoInteractions(service);
    }

    @Test
    void catchUpMissingDailyStatistics_afterDailyStart_delegatesOnce() {
        StatisticsStartupCatchupPolicy policy = mock(StatisticsStartupCatchupPolicy.class);
        DailyStatisticsAggregationService service = mock(DailyStatisticsAggregationService.class);
        when(policy.shouldCatchUp()).thenReturn(true);
        StatisticsStartupCatchupListener listener =
                new StatisticsStartupCatchupListener(policy, service);

        listener.catchUpMissingDailyStatistics();

        verify(service).catchUpMissingDailyStatistics();
    }

    @Test
    void catchUpMissingDailyStatistics_policyFails_keepsApplicationReady() {
        StatisticsStartupCatchupPolicy policy = mock(StatisticsStartupCatchupPolicy.class);
        DailyStatisticsAggregationService service = mock(DailyStatisticsAggregationService.class);
        when(policy.shouldCatchUp()).thenThrow(new IllegalStateException("시각 조회 실패"));
        StatisticsStartupCatchupListener listener =
                new StatisticsStartupCatchupListener(policy, service);

        assertThatCode(listener::catchUpMissingDailyStatistics).doesNotThrowAnyException();

        verifyNoInteractions(service);
    }

    @Test
    void catchUpMissingDailyStatistics_aggregationFails_keepsApplicationReady() {
        StatisticsStartupCatchupPolicy policy = mock(StatisticsStartupCatchupPolicy.class);
        DailyStatisticsAggregationService service = mock(DailyStatisticsAggregationService.class);
        when(policy.shouldCatchUp()).thenReturn(true);
        doThrow(new IllegalStateException("집계 실패"))
                .when(service)
                .catchUpMissingDailyStatistics();
        StatisticsStartupCatchupListener listener =
                new StatisticsStartupCatchupListener(policy, service);

        assertThatCode(listener::catchUpMissingDailyStatistics).doesNotThrowAnyException();

        verify(service).catchUpMissingDailyStatistics();
    }

    @Test
    void listener_applicationReadyEvent_invokesCatchup() throws Exception {
        Method method = StatisticsStartupCatchupListener.class
                .getMethod("catchUpMissingDailyStatistics");
        EventListener eventListener = method.getAnnotation(EventListener.class);

        assertThat(eventListener).isNotNull();
        assertThat(eventListener.value()).containsExactly(ApplicationReadyEvent.class);
    }

    @Test
    void listener_batchCommandEnabled_disablesStartupCatchup() {
        ConditionalOnProperty condition = StatisticsStartupCatchupListener.class
                .getAnnotation(ConditionalOnProperty.class);

        assertThat(condition).isNotNull();
        assertThat(condition.prefix()).isEqualTo("app.statistics");
        assertThat(condition.name()).containsExactly("backfill.enabled", "rebuild.enabled");
        assertThat(condition.havingValue()).isEqualTo("false");
        assertThat(condition.matchIfMissing()).isTrue();
    }
}
