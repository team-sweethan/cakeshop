package com.cakeshop.domain.statistics.query;

import static java.time.DayOfWeek.MONDAY;
import static java.time.temporal.TemporalAdjusters.previousOrSame;
import static java.time.temporal.ChronoUnit.WEEKS;

import com.cakeshop.domain.statistics.dto.view.DailyStatisticsRow;
import com.cakeshop.domain.statistics.dto.view.StatisticsTrendView;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Component;

/** 일별 통계를 선택한 달의 주차별 추이로 합산한다. */
@Component
public class MonthlyStatisticsTrendAggregator {

    private static final DateTimeFormatter DATE_LABEL_FORMATTER =
            DateTimeFormatter.ofPattern("MM.dd");

    /** 월요일부터 일요일까지를 한 주로 묶되 선택한 월의 날짜만 합산한다. */
    public List<StatisticsTrendView> aggregate(
            List<DailyStatisticsRow> rows,
            YearMonth yearMonth
    ) {
        Objects.requireNonNull(rows, "일별 통계는 필수입니다.");
        Objects.requireNonNull(yearMonth, "조회 연월은 필수입니다.");

        Map<LocalDate, List<DailyStatisticsRow>> rowsByWeek = new LinkedHashMap<>();
        rows.stream()
                .filter(row -> YearMonth.from(row.date()).equals(yearMonth))
                .sorted(Comparator.comparing(DailyStatisticsRow::date))
                .forEach(row -> rowsByWeek
                        .computeIfAbsent(
                                row.date().with(previousOrSame(MONDAY)),
                                ignored -> new ArrayList<>()
                        )
                        .add(row));

        List<StatisticsTrendView> trends = new ArrayList<>();
        LocalDate firstWeekStart = yearMonth.atDay(1).with(previousOrSame(MONDAY));
        for (Map.Entry<LocalDate, List<DailyStatisticsRow>> entry : rowsByWeek.entrySet()) {
            LocalDate startDate = latest(entry.getKey(), yearMonth.atDay(1));
            LocalDate endDate = earliest(entry.getKey().plusDays(6), yearMonth.atEndOfMonth());
            List<DailyStatisticsRow> weekRows = entry.getValue();
            int weekNumber = Math.toIntExact(WEEKS.between(firstWeekStart, entry.getKey()) + 1);

            long orderCount = weekRows.stream()
                    .mapToLong(DailyStatisticsRow::totalOrderCount)
                    .sum();
            BigDecimal salesAmount = weekRows.stream()
                    .map(DailyStatisticsRow::totalSalesAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            trends.add(new StatisticsTrendView(
                    createAxisLabel(weekNumber, startDate, endDate),
                    startDate,
                    endDate,
                    orderCount,
                    salesAmount
            ));
        }

        return List.copyOf(trends);
    }

    private String createAxisLabel(
            int weekNumber,
            LocalDate startDate,
            LocalDate endDate
    ) {
        return "%d주차 (%s~%s)".formatted(
                weekNumber,
                startDate.format(DATE_LABEL_FORMATTER),
                endDate.format(DATE_LABEL_FORMATTER)
        );
    }

    private LocalDate latest(LocalDate first, LocalDate second) {
        return first.isAfter(second) ? first : second;
    }

    private LocalDate earliest(LocalDate first, LocalDate second) {
        return first.isBefore(second) ? first : second;
    }
}
