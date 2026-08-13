package com.cakeshop.domain.statistics.query;

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
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PeriodStatisticsReadModelQueryService {

    private final PeriodStatisticsReadModelMapper mapper;
    private final Clock clock;
    private final StatisticsPeriodResolver periodResolver;
    private final MonthlyStatisticsTrendAggregator monthlyTrendAggregator;

    /** 조회 유형에 맞는 확정 주문·매출 요약과 기간별 추이를 조회한다. */
    @Transactional(readOnly = true)
    public PeriodStatisticsView getStatistics(StatisticsSearchForm form) {
        LocalDate latestSelectableDate = getLatestSelectableDate();
        boolean recentWeekSearch = isRecentWeekSearch(form);
        StatisticsDateRange range = recentWeekSearch
                ? null
                : periodResolver.resolve(form, latestSelectableDate);

        LocalDate latestContinuousDate =
                mapper.findLatestContinuousStatisticsDate(latestSelectableDate);
        if (latestContinuousDate == null) {
            throw new BusinessException(StatisticsErrorCode.STATISTICS_NOT_READY);
        }

        if (recentWeekSearch) {
            range = periodResolver.resolve(form, latestContinuousDate);
        }
        List<DailyStatisticsRow> rows = mapper.findDailyStatistics(
                range.startDate(),
                range.endDate()
        );
        if (recentWeekSearch && !rows.isEmpty()) {
            range = new StatisticsDateRange(rows.getFirst().date(), range.endDate());
        }
        validateCompletedRange(rows, range);

        return createView(
                range,
                rows,
                form.getPeriodType(),
                latestContinuousDate.isBefore(latestSelectableDate)
        );
    }

    /** 통계 화면에서 선택할 수 있는 마지막 날짜를 반환한다. */
    public LocalDate getLatestSelectableDate() {
        return LocalDate.now(clock).minusDays(1);
    }

    private boolean isRecentWeekSearch(StatisticsSearchForm form) {
        return form != null
                && form.getPeriodType() == StatisticsPeriodType.RECENT_WEEK
                && form.getStartDate() == null
                && form.getEndDate() == null;
    }

    private void validateCompletedRange(
            List<DailyStatisticsRow> rows,
            StatisticsDateRange range
    ) {
        long expectedDays = ChronoUnit.DAYS.between(range.startDate(), range.endDate()) + 1;
        if (rows.size() != expectedDays) {
            throw new BusinessException(StatisticsErrorCode.STATISTICS_NOT_READY);
        }

        for (int index = 0; index < rows.size(); index++) {
            if (!rows.get(index).date().equals(range.startDate().plusDays(index))) {
                throw new BusinessException(StatisticsErrorCode.STATISTICS_NOT_READY);
            }
        }
    }

    private PeriodStatisticsView createView(
            StatisticsDateRange range,
            List<DailyStatisticsRow> rows,
            StatisticsPeriodType periodType,
            boolean aggregationDelayed
    ) {
        long totalOrderCount = rows.stream()
                .mapToLong(DailyStatisticsRow::totalOrderCount)
                .sum();
        long completedOrderCount = rows.stream()
                .mapToLong(DailyStatisticsRow::completedOrderCount)
                .sum();
        long canceledOrderCount = rows.stream()
                .mapToLong(DailyStatisticsRow::canceledOrderCount)
                .sum();
        BigDecimal totalSalesAmount = rows.stream()
                .map(DailyStatisticsRow::totalSalesAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        List<StatisticsTrendView> trends = periodType == StatisticsPeriodType.MONTHLY
                ? monthlyTrendAggregator.aggregate(rows, YearMonth.from(range.startDate()))
                : rows.stream()
                        .map(row -> StatisticsTrendView.daily(
                                row.date(),
                                row.totalOrderCount(),
                                row.totalSalesAmount()
                        ))
                        .toList();

        return new PeriodStatisticsView(
                range.startDate(),
                range.endDate(),
                totalOrderCount,
                completedOrderCount,
                canceledOrderCount,
                totalSalesAmount,
                trends,
                aggregationDelayed
        );
    }
}
