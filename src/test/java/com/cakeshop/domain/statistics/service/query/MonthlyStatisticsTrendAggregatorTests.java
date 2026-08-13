package com.cakeshop.domain.statistics.service.query;

import static org.assertj.core.api.Assertions.assertThat;

import com.cakeshop.domain.statistics.dto.view.DailyStatisticsRow;
import com.cakeshop.domain.statistics.dto.view.StatisticsTrendView;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.Test;

class MonthlyStatisticsTrendAggregatorTests {

    private final MonthlyStatisticsTrendAggregator aggregator =
            new MonthlyStatisticsTrendAggregator();

    @Test
    void aggregate_sixWeekMonth_returnsPartialWeeksAndDateRangeLabels() {
        YearMonth yearMonth = YearMonth.of(2026, 8);
        List<DailyStatisticsRow> rows = yearMonth.atDay(1)
                .datesUntil(yearMonth.atEndOfMonth().plusDays(1))
                .map(date -> row(date, 1, "100"))
                .toList();

        List<StatisticsTrendView> trends = aggregator.aggregate(rows, yearMonth);

        assertThat(trends).containsExactly(
                trend("1주차 (08.01~08.02)", "2026-08-01", "2026-08-02", 2, "200"),
                trend("2주차 (08.03~08.09)", "2026-08-03", "2026-08-09", 7, "700"),
                trend("3주차 (08.10~08.16)", "2026-08-10", "2026-08-16", 7, "700"),
                trend("4주차 (08.17~08.23)", "2026-08-17", "2026-08-23", 7, "700"),
                trend("5주차 (08.24~08.30)", "2026-08-24", "2026-08-30", 7, "700"),
                trend("6주차 (08.31~08.31)", "2026-08-31", "2026-08-31", 1, "100")
        );
    }

    @Test
    void aggregate_rowsAcrossMonthBoundary_excludesDatesOutsideSelectedMonth() {
        YearMonth yearMonth = YearMonth.of(2026, 8);
        List<DailyStatisticsRow> rows = LocalDate.of(2026, 7, 27)
                .datesUntil(LocalDate.of(2026, 8, 3))
                .map(date -> row(date, 1, "100"))
                .toList();

        List<StatisticsTrendView> trends = aggregator.aggregate(rows, yearMonth);

        assertThat(trends).containsExactly(
                trend("1주차 (08.01~08.02)", "2026-08-01", "2026-08-02", 2, "200")
        );
    }

    @Test
    void aggregate_unsortedRows_returnsWeeksInDateOrder() {
        YearMonth yearMonth = YearMonth.of(2026, 7);
        List<DailyStatisticsRow> rows = List.of(
                row(LocalDate.of(2026, 7, 30), 2, "200"),
                row(LocalDate.of(2026, 7, 1), 1, "100")
        );

        List<StatisticsTrendView> trends = aggregator.aggregate(rows, yearMonth);

        assertThat(trends)
                .extracting(StatisticsTrendView::axisLabel)
                .containsExactly(
                        "1주차 (07.01~07.05)",
                        "5주차 (07.27~07.31)"
                );
    }

    private DailyStatisticsRow row(
            LocalDate date,
            long orderCount,
            String salesAmount
    ) {
        return new DailyStatisticsRow(
                date,
                orderCount,
                0,
                0,
                new BigDecimal(salesAmount)
        );
    }

    private StatisticsTrendView trend(
            String label,
            String startDate,
            String endDate,
            long orderCount,
            String salesAmount
    ) {
        return new StatisticsTrendView(
                label,
                LocalDate.parse(startDate),
                LocalDate.parse(endDate),
                orderCount,
                new BigDecimal(salesAmount)
        );
    }
}
