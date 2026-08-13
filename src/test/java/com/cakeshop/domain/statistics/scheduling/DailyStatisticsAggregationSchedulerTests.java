package com.cakeshop.domain.statistics.scheduling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.cakeshop.domain.statistics.aggregation.DailyStatisticsAggregationService;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;

class DailyStatisticsAggregationSchedulerTests {

    @Test
    void aggregateDailyStatistics_scheduledInvocation_delegatesToService() {
        DailyStatisticsAggregationService service = mock(DailyStatisticsAggregationService.class);
        DailyStatisticsAggregationScheduler scheduler =
                new DailyStatisticsAggregationScheduler(service);

        scheduler.aggregateDailyStatistics();

        verify(service).aggregateDailyStatistics();
    }

    @Test
    void aggregateDailyStatistics_schedule_runsDailyAtSeoulMidnightPastTen() throws Exception {
        Method method = DailyStatisticsAggregationScheduler.class
                .getMethod("aggregateDailyStatistics");
        Scheduled scheduled = method.getAnnotation(Scheduled.class);

        assertThat(scheduled).isNotNull();
        assertThat(scheduled.cron()).isEqualTo("0 10 0 * * *");
        assertThat(scheduled.zone()).isEqualTo("Asia/Seoul");
    }
}
