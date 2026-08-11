package com.cakeshop.domain.statistics.dto;

import java.time.LocalDate;
import java.util.Objects;

/** 운영자가 수동 재집계를 요청한 기간을 전달한다. */
public record StatisticsRebuildRequest(
        LocalDate startDate,
        LocalDate endDate
) {

    public StatisticsRebuildRequest {
        Objects.requireNonNull(startDate, "재집계 시작일은 필수입니다.");
    }
}
