package com.cakeshop.domain.statistics.service;

import com.cakeshop.domain.statistics.dto.view.DailyStatisticsRow;
import com.cakeshop.domain.statistics.dto.view.DailyStatisticsView;
import com.cakeshop.domain.statistics.dto.view.PeriodStatisticsView;
import com.cakeshop.domain.statistics.error.StatisticsErrorCode;
import com.cakeshop.domain.statistics.mapper.PeriodStatisticsReadModelMapper;
import com.cakeshop.global.error.BusinessException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PeriodStatisticsReadModelQueryService {

    private static final long DEFAULT_RANGE_DAYS = 7;
    private static final long MAX_RANGE_DAYS = 366;

    private final PeriodStatisticsReadModelMapper mapper;
    private final Clock clock;

    /** 조회 기간의 확정 주문·매출 요약과 일별 추이를 조회한다. */
    @Transactional(readOnly = true)
    public PeriodStatisticsView getStatistics(LocalDate startDate, LocalDate endDate) {
        LocalDate latestSelectableDate = getLatestSelectableDate();
        boolean defaultSearch = startDate == null && endDate == null;
        if (!defaultSearch) {
            validateRequestedRange(startDate, endDate, latestSelectableDate);
        }

        LocalDate latestContinuousDate =
                mapper.findLatestContinuousStatisticsDate(latestSelectableDate);
        if (latestContinuousDate == null) {
            throw new BusinessException(StatisticsErrorCode.STATISTICS_NOT_READY);
        }

        DateRange range = defaultSearch
                ? resolveDefaultRange(latestContinuousDate)
                : new DateRange(startDate, endDate);
        List<DailyStatisticsRow> rows = mapper.findDailyStatistics(
                range.startDate(),
                range.endDate()
        );
        if (defaultSearch && !rows.isEmpty()) {
            range = new DateRange(rows.getFirst().date(), range.endDate());
        }
        validateCompletedRange(rows, range);

        return createView(
                range,
                rows,
                latestContinuousDate.isBefore(latestSelectableDate)
        );
    }

    /** 통계 화면에서 선택할 수 있는 마지막 날짜를 반환한다. */
    public LocalDate getLatestSelectableDate() {
        return LocalDate.now(clock).minusDays(1);
    }

    private DateRange resolveDefaultRange(LocalDate latestContinuousDate) {
        return new DateRange(
                latestContinuousDate.minusDays(DEFAULT_RANGE_DAYS - 1),
                latestContinuousDate
        );
    }

    private void validateRequestedRange(
            LocalDate startDate,
            LocalDate endDate,
            LocalDate latestSelectableDate
    ) {
        if (startDate == null
                || endDate == null
                || startDate.isAfter(endDate)
                || endDate.isAfter(latestSelectableDate)
                || ChronoUnit.DAYS.between(startDate, endDate) >= MAX_RANGE_DAYS) {
            throw new BusinessException(StatisticsErrorCode.INVALID_DATE_RANGE);
        }
    }

    private void validateCompletedRange(
            List<DailyStatisticsRow> rows,
            DateRange range
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
            DateRange range,
            List<DailyStatisticsRow> rows,
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
        List<DailyStatisticsView> dailyStatistics = rows.stream()
                .map(row -> new DailyStatisticsView(
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
                dailyStatistics,
                aggregationDelayed
        );
    }

    private record DateRange(LocalDate startDate, LocalDate endDate) {
    }
}
