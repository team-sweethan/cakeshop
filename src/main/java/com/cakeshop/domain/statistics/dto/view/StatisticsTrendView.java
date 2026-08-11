package com.cakeshop.domain.statistics.dto.view;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/** 관리자 기간별 통계 그래프의 한 조회 구간을 전달한다. */
public record StatisticsTrendView(
        String axisLabel,
        LocalDate startDate,
        LocalDate endDate,
        long orderCount,
        BigDecimal salesAmount
) {

    private static final DateTimeFormatter DAILY_LABEL_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy.MM.dd");

    /** 하루의 주문·매출 추이를 생성한다. */
    public static StatisticsTrendView daily(
            LocalDate date,
            long orderCount,
            BigDecimal salesAmount
    ) {
        return new StatisticsTrendView(
                date.format(DAILY_LABEL_FORMATTER),
                date,
                date,
                orderCount,
                salesAmount
        );
    }
}
