package com.cakeshop.domain.statistics.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.cakeshop.domain.statistics.dto.view.DailyOrderStatisticsView;
import com.cakeshop.domain.statistics.dto.view.DailySalesStatisticsView;
import com.cakeshop.domain.statistics.dto.view.DailyStatisticsView;
import com.cakeshop.domain.statistics.dto.view.PeriodStatisticsView;
import com.cakeshop.domain.statistics.error.StatisticsErrorCode;
import com.cakeshop.domain.statistics.mapper.PeriodStatisticsReadModelMapper;
import com.cakeshop.global.error.BusinessException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PeriodStatisticsReadModelQueryServiceTests {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-08-09T15:30:00Z"),
            SEOUL
    );

    @Mock
    private PeriodStatisticsReadModelMapper mapper;

    @Test
    void getStatistics_noDateRange_returnsRecentSevenDaysFilledWithZeros() {
        LocalDate startDate = LocalDate.of(2026, 8, 4);
        LocalDate endDate = LocalDate.of(2026, 8, 10);
        LocalDateTime start = startDate.atStartOfDay();
        LocalDateTime end = endDate.plusDays(1).atStartOfDay();
        when(mapper.findDailyOrderStatistics(start, end)).thenReturn(List.of());
        when(mapper.findDailySalesStatistics(start, end)).thenReturn(List.of());
        PeriodStatisticsReadModelQueryService service = service();

        PeriodStatisticsView statistics = service.getStatistics(null, null);

        assertThat(statistics.startDate()).isEqualTo(startDate);
        assertThat(statistics.endDate()).isEqualTo(endDate);
        assertThat(statistics.totalOrderCount()).isZero();
        assertThat(statistics.totalSalesAmount()).isZero();
        assertThat(statistics.dailyStatistics())
                .hasSize(7)
                .first()
                .isEqualTo(new DailyStatisticsView(startDate, 0, BigDecimal.ZERO));
        assertThat(statistics.dailyStatistics())
                .last()
                .isEqualTo(new DailyStatisticsView(endDate, 0, BigDecimal.ZERO));
    }

    @Test
    void getStatistics_dailyResults_mergesDatesAndCalculatesSummary() {
        LocalDate startDate = LocalDate.of(2026, 8, 8);
        LocalDate endDate = LocalDate.of(2026, 8, 10);
        LocalDateTime start = startDate.atStartOfDay();
        LocalDateTime end = endDate.plusDays(1).atStartOfDay();
        when(mapper.findDailyOrderStatistics(start, end)).thenReturn(List.of(
                new DailyOrderStatisticsView(startDate, 3, 1, 1),
                new DailyOrderStatisticsView(endDate, 2, 0, 1)
        ));
        when(mapper.findDailySalesStatistics(start, end)).thenReturn(List.of(
                new DailySalesStatisticsView(startDate.plusDays(1), new BigDecimal("50000")),
                new DailySalesStatisticsView(endDate, new BigDecimal("20000"))
        ));
        PeriodStatisticsReadModelQueryService service = service();

        PeriodStatisticsView statistics = service.getStatistics(startDate, endDate);

        assertThat(statistics.totalOrderCount()).isEqualTo(5);
        assertThat(statistics.completedOrderCount()).isEqualTo(1);
        assertThat(statistics.canceledOrderCount()).isEqualTo(2);
        assertThat(statistics.totalSalesAmount()).isEqualByComparingTo("70000");
        assertThat(statistics.dailyStatistics()).containsExactly(
                new DailyStatisticsView(startDate, 3, BigDecimal.ZERO),
                new DailyStatisticsView(startDate.plusDays(1), 0, new BigDecimal("50000")),
                new DailyStatisticsView(endDate, 2, new BigDecimal("20000"))
        );
        verify(mapper).findDailyOrderStatistics(start, end);
        verify(mapper).findDailySalesStatistics(start, end);
    }

    @Test
    void getStatistics_endDateAfterToday_rejectsFutureRange() {
        PeriodStatisticsReadModelQueryService service = service();

        assertThatThrownBy(() -> service.getStatistics(
                LocalDate.of(2026, 8, 10),
                LocalDate.of(2026, 8, 11)
        ))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(StatisticsErrorCode.INVALID_DATE_RANGE)
                );
        verifyNoInteractions(mapper);
    }

    private PeriodStatisticsReadModelQueryService service() {
        return new PeriodStatisticsReadModelQueryService(mapper, CLOCK);
    }
}
