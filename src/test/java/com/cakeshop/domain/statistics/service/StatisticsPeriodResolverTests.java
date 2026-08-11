package com.cakeshop.domain.statistics.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cakeshop.domain.statistics.dto.form.StatisticsPeriodType;
import com.cakeshop.domain.statistics.dto.form.StatisticsSearchForm;
import com.cakeshop.domain.statistics.error.StatisticsErrorCode;
import com.cakeshop.global.error.BusinessException;
import java.time.LocalDate;
import java.time.YearMonth;
import org.junit.jupiter.api.Test;

class StatisticsPeriodResolverTests {

    private static final LocalDate LATEST_SELECTABLE_DATE = LocalDate.of(2026, 8, 10);

    private final StatisticsPeriodResolver resolver = new StatisticsPeriodResolver();

    @Test
    void resolve_defaultRange_returnsSevenDaysEndingOnLatestSelectableDate() {
        StatisticsDateRange range = resolver.resolve(
                new StatisticsSearchForm(),
                LATEST_SELECTABLE_DATE
        );

        assertThat(range).isEqualTo(new StatisticsDateRange(
                LocalDate.of(2026, 8, 4),
                LocalDate.of(2026, 8, 10)
        ));
    }

    @Test
    void resolve_requestedRange_returnsInputDates() {
        StatisticsSearchForm form = new StatisticsSearchForm();
        form.setStartDate(LocalDate.of(2026, 7, 1));
        form.setEndDate(LocalDate.of(2026, 7, 31));

        StatisticsDateRange range = resolver.resolve(form, LATEST_SELECTABLE_DATE);

        assertThat(range).isEqualTo(new StatisticsDateRange(
                LocalDate.of(2026, 7, 1),
                LocalDate.of(2026, 7, 31)
        ));
    }

    @Test
    void resolve_defaultWeekly_returnsLatestCompletedMondayToSunday() {
        StatisticsSearchForm form = new StatisticsSearchForm();
        form.setPeriodType(StatisticsPeriodType.WEEKLY);

        StatisticsDateRange range = resolver.resolve(form, LATEST_SELECTABLE_DATE);

        assertThat(range).isEqualTo(new StatisticsDateRange(
                LocalDate.of(2026, 8, 3),
                LocalDate.of(2026, 8, 9)
        ));
    }

    @Test
    void resolve_weekAcrossMonthEnd_includesDatesAcrossMonthBoundary() {
        StatisticsSearchForm form = new StatisticsSearchForm();
        form.setPeriodType(StatisticsPeriodType.WEEKLY);
        form.setWeek("2026-W31");

        StatisticsDateRange range = resolver.resolve(form, LATEST_SELECTABLE_DATE);

        assertThat(range).isEqualTo(new StatisticsDateRange(
                LocalDate.of(2026, 7, 27),
                LocalDate.of(2026, 8, 2)
        ));
    }

    @Test
    void resolve_currentWeek_rejectsIncompleteWeek() {
        StatisticsSearchForm form = new StatisticsSearchForm();
        form.setPeriodType(StatisticsPeriodType.WEEKLY);
        form.setWeek("2026-W33");

        assertInvalidDateRange(() -> resolver.resolve(form, LATEST_SELECTABLE_DATE));
    }

    @Test
    void resolve_nonexistentIsoWeek_rejectsWeek() {
        StatisticsSearchForm form = new StatisticsSearchForm();
        form.setPeriodType(StatisticsPeriodType.WEEKLY);
        form.setWeek("2025-W53");

        assertInvalidDateRange(() -> resolver.resolve(form, LATEST_SELECTABLE_DATE));
    }

    @Test
    void resolve_defaultMonthly_returnsLatestCompletedMonth() {
        StatisticsSearchForm form = new StatisticsSearchForm();
        form.setPeriodType(StatisticsPeriodType.MONTHLY);

        StatisticsDateRange range = resolver.resolve(form, LATEST_SELECTABLE_DATE);

        assertThat(range).isEqualTo(new StatisticsDateRange(
                LocalDate.of(2026, 7, 1),
                LocalDate.of(2026, 7, 31)
        ));
    }

    @Test
    void resolve_monthlyLeapYearFebruary_returnsLeapDay() {
        StatisticsSearchForm form = new StatisticsSearchForm();
        form.setPeriodType(StatisticsPeriodType.MONTHLY);
        form.setYearMonth(YearMonth.of(2024, 2));

        StatisticsDateRange range = resolver.resolve(form, LATEST_SELECTABLE_DATE);

        assertThat(range).isEqualTo(new StatisticsDateRange(
                LocalDate.of(2024, 2, 1),
                LocalDate.of(2024, 2, 29)
        ));
    }

    @Test
    void resolve_currentMonth_rejectsIncompleteMonth() {
        StatisticsSearchForm form = new StatisticsSearchForm();
        form.setPeriodType(StatisticsPeriodType.MONTHLY);
        form.setYearMonth(YearMonth.of(2026, 8));

        assertInvalidDateRange(() -> resolver.resolve(form, LATEST_SELECTABLE_DATE));
    }

    @Test
    void resolve_reversedRange_rejectsRange() {
        StatisticsSearchForm form = new StatisticsSearchForm();
        form.setStartDate(LocalDate.of(2026, 8, 2));
        form.setEndDate(LocalDate.of(2026, 8, 1));

        assertInvalidDateRange(() -> resolver.resolve(form, LATEST_SELECTABLE_DATE));
    }

    @Test
    void resolve_rangeOver366Days_rejectsRange() {
        StatisticsSearchForm form = new StatisticsSearchForm();
        form.setStartDate(LocalDate.of(2025, 1, 1));
        form.setEndDate(LocalDate.of(2026, 1, 2));

        assertInvalidDateRange(() -> resolver.resolve(form, LATEST_SELECTABLE_DATE));
    }

    private void assertInvalidDateRange(Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(StatisticsErrorCode.INVALID_DATE_RANGE)
                );
    }
}
