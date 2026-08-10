package com.cakeshop.domain.statistics.service;

import com.cakeshop.domain.statistics.dto.view.DailyOrderStatisticsView;
import com.cakeshop.domain.statistics.dto.view.DailySalesStatisticsView;
import com.cakeshop.domain.statistics.dto.view.DailyStatisticsView;
import com.cakeshop.domain.statistics.dto.view.PeriodStatisticsView;
import com.cakeshop.domain.statistics.error.StatisticsErrorCode;
import com.cakeshop.domain.statistics.mapper.PeriodStatisticsReadModelMapper;
import com.cakeshop.global.error.BusinessException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PeriodStatisticsReadModelQueryService {

    private static final long DEFAULT_RANGE_DAYS = 30;
    private static final long MAX_RANGE_DAYS = 366;

    private final PeriodStatisticsReadModelMapper mapper;
    private final Clock clock;

    /** 조회 기간의 주문·매출 요약과 일별 추이를 조회한다. */
    @Transactional(readOnly = true)
    public PeriodStatisticsView getStatistics(LocalDate startDate, LocalDate endDate) {
        DateRange range = resolveRange(startDate, endDate);
        LocalDateTime start = range.startDate().atStartOfDay();
        LocalDateTime end = range.endDate().plusDays(1).atStartOfDay();

        Map<LocalDate, DailyOrderStatisticsView> orderStatisticsByDate =
                mapper.findDailyOrderStatistics(start, end).stream()
                        .collect(Collectors.toMap(
                                DailyOrderStatisticsView::date,
                                Function.identity()
                        ));
        Map<LocalDate, DailySalesStatisticsView> salesStatisticsByDate =
                mapper.findDailySalesStatistics(start, end).stream()
                        .collect(Collectors.toMap(
                                DailySalesStatisticsView::date,
                                Function.identity()
                        ));

        long totalOrderCount = 0;
        long completedOrderCount = 0;
        long canceledOrderCount = 0;
        BigDecimal totalSalesAmount = BigDecimal.ZERO;
        List<DailyStatisticsView> dailyStatistics = new ArrayList<>();

        for (LocalDate date = range.startDate(); !date.isAfter(range.endDate()); date = date.plusDays(1)) {
            DailyOrderStatisticsView orderStatistics = orderStatisticsByDate.get(date);
            DailySalesStatisticsView salesStatistics = salesStatisticsByDate.get(date);

            long dailyOrderCount = orderStatistics == null ? 0 : orderStatistics.totalOrderCount();
            BigDecimal dailySalesAmount = salesStatistics == null
                    ? BigDecimal.ZERO
                    : salesStatistics.salesAmount();

            if (orderStatistics != null) {
                totalOrderCount += orderStatistics.totalOrderCount();
                completedOrderCount += orderStatistics.completedOrderCount();
                canceledOrderCount += orderStatistics.canceledOrderCount();
            }
            totalSalesAmount = totalSalesAmount.add(dailySalesAmount);
            dailyStatistics.add(new DailyStatisticsView(date, dailyOrderCount, dailySalesAmount));
        }

        return new PeriodStatisticsView(
                range.startDate(),
                range.endDate(),
                totalOrderCount,
                completedOrderCount,
                canceledOrderCount,
                totalSalesAmount,
                List.copyOf(dailyStatistics)
        );
    }

    /** 조회 기간의 기본값을 적용하고 유효성을 검증한다. */
    private DateRange resolveRange(LocalDate startDate, LocalDate endDate) {
        LocalDate today = LocalDate.now(clock);
        if (startDate == null && endDate == null) {
            return new DateRange(today.minusDays(DEFAULT_RANGE_DAYS - 1), today);
        }
        if (startDate == null
                || endDate == null
                || startDate.isAfter(endDate)
                || endDate.isAfter(today)
                || ChronoUnit.DAYS.between(startDate, endDate) >= MAX_RANGE_DAYS) {
            throw new BusinessException(StatisticsErrorCode.INVALID_DATE_RANGE);
        }
        return new DateRange(startDate, endDate);
    }

    private record DateRange(LocalDate startDate, LocalDate endDate) {
    }
}
