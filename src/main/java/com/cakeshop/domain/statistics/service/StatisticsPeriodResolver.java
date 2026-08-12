package com.cakeshop.domain.statistics.service;

import static java.time.DayOfWeek.SUNDAY;
import static java.time.temporal.TemporalAdjusters.previousOrSame;

import com.cakeshop.domain.statistics.dto.form.StatisticsPeriodType;
import com.cakeshop.domain.statistics.dto.form.StatisticsSearchForm;
import com.cakeshop.domain.statistics.error.StatisticsErrorCode;
import com.cakeshop.global.error.BusinessException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import org.springframework.stereotype.Component;

/** 조회 유형과 입력값을 실제 통계 조회 기간으로 변환한다. */
@Component
public class StatisticsPeriodResolver {

    private static final long DEFAULT_RANGE_DAYS = 7;
    private static final long MAX_RANGE_DAYS = 366;

    /** 조회 유형에 맞는 시작일과 종료일을 계산한다. */
    public StatisticsDateRange resolve(
            StatisticsSearchForm form,
            LocalDate latestSelectableDate
    ) {
        if (form == null || latestSelectableDate == null || form.getPeriodType() == null) {
            throw invalidDateRange();
        }

        return switch (form.getPeriodType()) {
            case RECENT_WEEK -> resolveRecentWeek(latestSelectableDate);
            case RANGE -> resolveRange(form, latestSelectableDate);
            case WEEKLY -> resolveWeekly(form, latestSelectableDate);
            case MONTHLY -> resolveMonthly(form, latestSelectableDate);
        };
    }

    private StatisticsDateRange resolveRecentWeek(LocalDate latestSelectableDate) {
        return new StatisticsDateRange(
                latestSelectableDate.minusDays(DEFAULT_RANGE_DAYS - 1),
                latestSelectableDate
        );
    }

    private StatisticsDateRange resolveRange(
            StatisticsSearchForm form,
            LocalDate latestSelectableDate
    ) {
        LocalDate startDate = form.getStartDate();
        LocalDate endDate = form.getEndDate();
        if (startDate == null
                || endDate == null
                || startDate.isAfter(endDate)
                || endDate.isAfter(latestSelectableDate)
                || ChronoUnit.DAYS.between(startDate, endDate) >= MAX_RANGE_DAYS) {
            throw invalidDateRange();
        }

        return new StatisticsDateRange(startDate, endDate);
    }

    private StatisticsDateRange resolveWeekly(
            StatisticsSearchForm form,
            LocalDate latestSelectableDate
    ) {
        String week = form.getWeek();
        if (week == null) {
            LocalDate lastCompletedSunday = latestSelectableDate.with(previousOrSame(SUNDAY));
            return new StatisticsDateRange(
                    lastCompletedSunday.minusDays(6),
                    lastCompletedSunday
            );
        }

        LocalDate startDate;
        try {
            startDate = LocalDate.parse(week + "-1", DateTimeFormatter.ISO_WEEK_DATE);
        } catch (DateTimeParseException exception) {
            throw invalidDateRange();
        }
        LocalDate endDate = startDate.plusDays(6);
        if (endDate.isAfter(latestSelectableDate)) {
            throw invalidDateRange();
        }

        return new StatisticsDateRange(startDate, endDate);
    }

    private StatisticsDateRange resolveMonthly(
            StatisticsSearchForm form,
            LocalDate latestSelectableDate
    ) {
        YearMonth yearMonth = form.getYearMonth();
        if (yearMonth == null) {
            yearMonth = YearMonth.from(latestSelectableDate);
            if (yearMonth.atEndOfMonth().isAfter(latestSelectableDate)) {
                yearMonth = yearMonth.minusMonths(1);
            }
        }

        LocalDate startDate = yearMonth.atDay(1);
        LocalDate endDate = yearMonth.atEndOfMonth();
        if (endDate.isAfter(latestSelectableDate)) {
            throw invalidDateRange();
        }

        return new StatisticsDateRange(startDate, endDate);
    }

    private BusinessException invalidDateRange() {
        return new BusinessException(StatisticsErrorCode.INVALID_DATE_RANGE);
    }
}
