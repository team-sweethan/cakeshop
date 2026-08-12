package com.cakeshop.domain.statistics.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.cakeshop.domain.statistics.dto.form.StatisticsPeriodType;
import com.cakeshop.domain.statistics.dto.form.StatisticsSearchForm;
import com.cakeshop.domain.statistics.dto.view.DailyStatisticsRow;
import com.cakeshop.domain.statistics.dto.view.PeriodStatisticsView;
import com.cakeshop.domain.statistics.dto.view.StatisticsTrendView;
import com.cakeshop.domain.statistics.error.StatisticsErrorCode;
import com.cakeshop.domain.statistics.mapper.PeriodStatisticsReadModelMapper;
import com.cakeshop.global.error.BusinessException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
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
    private static final LocalDate YESTERDAY = LocalDate.of(2026, 8, 9);

    @Mock
    private PeriodStatisticsReadModelMapper mapper;

    @Test
    void getStatistics_recentWeek_returnsYesterdayRecentSevenDays() {
        LocalDate startDate = YESTERDAY.minusDays(6);
        List<DailyStatisticsRow> rows = rows(startDate, YESTERDAY);
        when(mapper.findLatestContinuousStatisticsDate(YESTERDAY)).thenReturn(YESTERDAY);
        when(mapper.findDailyStatistics(startDate, YESTERDAY)).thenReturn(rows);

        PeriodStatisticsView statistics = service().getStatistics(form(null, null));

        assertThat(statistics.startDate()).isEqualTo(startDate);
        assertThat(statistics.endDate()).isEqualTo(YESTERDAY);
        assertThat(statistics.trends()).hasSize(7);
        assertThat(statistics.aggregationDelayed()).isFalse();
    }

    @Test
    void getStatistics_recentWeekHasOnlyThreeCompletedDays_returnsAvailableRange() {
        LocalDate latestCompletedDate = YESTERDAY.minusDays(1);
        LocalDate completedStartDate = latestCompletedDate.minusDays(2);
        LocalDate requestedStartDate = latestCompletedDate.minusDays(6);
        List<DailyStatisticsRow> rows = rows(completedStartDate, latestCompletedDate);
        when(mapper.findLatestContinuousStatisticsDate(YESTERDAY))
                .thenReturn(latestCompletedDate);
        when(mapper.findDailyStatistics(requestedStartDate, latestCompletedDate))
                .thenReturn(rows);

        PeriodStatisticsView statistics = service().getStatistics(form(null, null));

        assertThat(statistics.startDate()).isEqualTo(completedStartDate);
        assertThat(statistics.endDate()).isEqualTo(latestCompletedDate);
        assertThat(statistics.trends()).hasSize(3);
        assertThat(statistics.aggregationDelayed()).isTrue();
    }

    @Test
    void getStatistics_dailyRows_calculatesSummaryFromAggregateTable() {
        LocalDate startDate = YESTERDAY.minusDays(1);
        List<DailyStatisticsRow> rows = List.of(
                new DailyStatisticsRow(startDate, 3, 2, 1, new BigDecimal("50000")),
                new DailyStatisticsRow(YESTERDAY, 2, 1, 0, new BigDecimal("20000"))
        );
        when(mapper.findLatestContinuousStatisticsDate(YESTERDAY)).thenReturn(YESTERDAY);
        when(mapper.findDailyStatistics(startDate, YESTERDAY)).thenReturn(rows);

        PeriodStatisticsView statistics = service().getStatistics(form(startDate, YESTERDAY));

        assertThat(statistics.totalOrderCount()).isEqualTo(5);
        assertThat(statistics.completedOrderCount()).isEqualTo(3);
        assertThat(statistics.canceledOrderCount()).isOne();
        assertThat(statistics.totalSalesAmount()).isEqualByComparingTo("70000");
        assertThat(statistics.trends()).containsExactly(
                StatisticsTrendView.daily(startDate, 3, new BigDecimal("50000")),
                StatisticsTrendView.daily(YESTERDAY, 2, new BigDecimal("20000"))
        );
    }

    @Test
    void getStatistics_weeklyAcrossMonthBoundary_returnsSevenDailyTrends() {
        LocalDate startDate = LocalDate.of(2026, 7, 27);
        LocalDate endDate = LocalDate.of(2026, 8, 2);
        StatisticsSearchForm form = new StatisticsSearchForm();
        form.setPeriodType(StatisticsPeriodType.WEEKLY);
        form.setWeek("2026-W31");
        when(mapper.findLatestContinuousStatisticsDate(YESTERDAY)).thenReturn(YESTERDAY);
        when(mapper.findDailyStatistics(startDate, endDate)).thenReturn(rows(startDate, endDate));

        PeriodStatisticsView statistics = service().getStatistics(form);

        assertThat(statistics.startDate()).isEqualTo(startDate);
        assertThat(statistics.endDate()).isEqualTo(endDate);
        assertThat(statistics.trends())
                .extracting(StatisticsTrendView::axisLabel)
                .containsExactly(
                        "2026.07.27",
                        "2026.07.28",
                        "2026.07.29",
                        "2026.07.30",
                        "2026.07.31",
                        "2026.08.01",
                        "2026.08.02"
                );
    }

    @Test
    void getStatistics_monthly_returnsWeeklyTrends() {
        YearMonth yearMonth = YearMonth.of(2026, 7);
        LocalDate startDate = yearMonth.atDay(1);
        LocalDate endDate = yearMonth.atEndOfMonth();
        StatisticsSearchForm form = new StatisticsSearchForm();
        form.setPeriodType(StatisticsPeriodType.MONTHLY);
        form.setYearMonth(yearMonth);
        when(mapper.findLatestContinuousStatisticsDate(YESTERDAY)).thenReturn(YESTERDAY);
        when(mapper.findDailyStatistics(startDate, endDate)).thenReturn(rows(startDate, endDate));

        PeriodStatisticsView statistics = service().getStatistics(form);

        assertThat(statistics.startDate()).isEqualTo(startDate);
        assertThat(statistics.endDate()).isEqualTo(endDate);
        assertThat(statistics.trends())
                .extracting(StatisticsTrendView::axisLabel)
                .containsExactly(
                        "1주차 (07.01~07.05)",
                        "2주차 (07.06~07.12)",
                        "3주차 (07.13~07.19)",
                        "4주차 (07.20~07.26)",
                        "5주차 (07.27~07.31)"
                );
    }

    @Test
    void getStatistics_requestedRangeContainsMissingDate_rejectsPartialResult() {
        LocalDate startDate = YESTERDAY.minusDays(2);
        when(mapper.findLatestContinuousStatisticsDate(YESTERDAY)).thenReturn(YESTERDAY);
        when(mapper.findDailyStatistics(startDate, YESTERDAY)).thenReturn(List.of(
                row(startDate),
                row(YESTERDAY)
        ));

        assertThatThrownBy(() -> service().getStatistics(form(startDate, YESTERDAY)))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(StatisticsErrorCode.STATISTICS_NOT_READY)
                );
    }

    @Test
    void getStatistics_noCompletedStatistics_rejectsNotReady() {
        when(mapper.findLatestContinuousStatisticsDate(YESTERDAY)).thenReturn(null);

        assertThatThrownBy(() -> service().getStatistics(form(null, null)))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(StatisticsErrorCode.STATISTICS_NOT_READY)
                );
    }

    @Test
    void getStatistics_endDateIsToday_rejectsRangeBeforeQuery() {
        LocalDate today = YESTERDAY.plusDays(1);

        assertThatThrownBy(() -> service().getStatistics(form(YESTERDAY, today)))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(StatisticsErrorCode.INVALID_DATE_RANGE)
                );
        verifyNoInteractions(mapper);
    }

    @Test
    void getLatestSelectableDate_returnsYesterdayInSeoul() {
        assertThat(service().getLatestSelectableDate()).isEqualTo(YESTERDAY);
        verifyNoInteractions(mapper);
    }

    private PeriodStatisticsReadModelQueryService service() {
        return new PeriodStatisticsReadModelQueryService(
                mapper,
                CLOCK,
                new StatisticsPeriodResolver(),
                new MonthlyStatisticsTrendAggregator()
        );
    }

    private StatisticsSearchForm form(LocalDate startDate, LocalDate endDate) {
        StatisticsSearchForm form = new StatisticsSearchForm();
        if (startDate != null || endDate != null) {
            form.setPeriodType(StatisticsPeriodType.RANGE);
        }
        form.setStartDate(startDate);
        form.setEndDate(endDate);
        return form;
    }

    private List<DailyStatisticsRow> rows(LocalDate startDate, LocalDate endDate) {
        return startDate.datesUntil(endDate.plusDays(1))
                .map(this::row)
                .toList();
    }

    private DailyStatisticsRow row(LocalDate date) {
        return new DailyStatisticsRow(date, 0, 0, 0, BigDecimal.ZERO);
    }
}
