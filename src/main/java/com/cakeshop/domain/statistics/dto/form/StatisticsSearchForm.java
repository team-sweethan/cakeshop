package com.cakeshop.domain.statistics.dto.form;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

/** 관리자 기간별 통계의 조회 기간을 입력받는다. */
@Getter
@Setter
public class StatisticsSearchForm {

    private static final long MAX_RANGE_DAYS = 366;

    /** 조회 유형이 없으면 기존 기간 조회로 처리한다. */
    @NotNull(message = "조회 유형을 선택해 주세요.")
    private StatisticsPeriodType periodType = StatisticsPeriodType.RANGE;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate startDate;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate endDate;

    /** ISO 주차 형식으로 선택한 월요일부터 일요일까지를 주간 조회한다. */
    @Pattern(
            regexp = "\\d{4}-W\\d{2}",
            message = "조회 주는 yyyy-Www 형식으로 입력해 주세요."
    )
    private String week;

    /** 빈 주 선택값은 기본 주간 조회로 처리한다. */
    public void setWeek(String week) {
        this.week = week == null || week.isBlank() ? null : week;
    }

    /** 선택한 연·월의 1일부터 말일까지를 월간 조회한다. */
    @DateTimeFormat(pattern = "yyyy-MM")
    private YearMonth yearMonth;

    @AssertTrue(message = "시작일과 종료일을 모두 입력해 주세요.")
    public boolean isDateRangeComplete() {
        return periodType != StatisticsPeriodType.RANGE
                || (startDate == null) == (endDate == null);
    }

    @AssertTrue(message = "시작일은 종료일보다 늦을 수 없습니다.")
    public boolean isDateRangeOrdered() {
        return periodType != StatisticsPeriodType.RANGE
                || startDate == null
                || endDate == null
                || !startDate.isAfter(endDate);
    }

    @AssertTrue(message = "조회 기간은 최대 366일까지 선택할 수 있습니다.")
    public boolean isDateRangeWithinLimit() {
        if (periodType != StatisticsPeriodType.RANGE
                || startDate == null
                || endDate == null
                || startDate.isAfter(endDate)) {
            return true;
        }

        return ChronoUnit.DAYS.between(startDate, endDate) < MAX_RANGE_DAYS;
    }
}
