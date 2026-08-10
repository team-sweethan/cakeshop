package com.cakeshop.domain.statistics.dto.form;

import jakarta.validation.constraints.AssertTrue;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

/** 관리자 기간별 통계의 조회 기간을 입력받는다. */
@Getter
@Setter
public class StatisticsSearchForm {

    private static final long MAX_RANGE_DAYS = 366;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate startDate;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate endDate;

    @AssertTrue(message = "시작일과 종료일을 모두 입력해 주세요.")
    public boolean isDateRangeComplete() {
        return (startDate == null) == (endDate == null);
    }

    @AssertTrue(message = "시작일은 종료일보다 늦을 수 없습니다.")
    public boolean isDateRangeOrdered() {
        return startDate == null || endDate == null || !startDate.isAfter(endDate);
    }

    @AssertTrue(message = "조회 기간은 최대 366일까지 선택할 수 있습니다.")
    public boolean isDateRangeWithinLimit() {
        if (startDate == null || endDate == null || startDate.isAfter(endDate)) {
            return true;
        }

        return ChronoUnit.DAYS.between(startDate, endDate) < MAX_RANGE_DAYS;
    }
}
