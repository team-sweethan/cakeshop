package com.cakeshop.domain.statistics.service;

import java.time.LocalDate;

/** 통계 조회에 사용할 시작일과 종료일. */
public record StatisticsDateRange(
        LocalDate startDate,
        LocalDate endDate
) {
}
