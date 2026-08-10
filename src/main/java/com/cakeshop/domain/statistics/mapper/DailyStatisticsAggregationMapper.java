package com.cakeshop.domain.statistics.mapper;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface DailyStatisticsAggregationMapper {

    /** 원본 주문·결제를 집계하여 해당 날짜의 확정 통계를 교체한다. */
    int replaceDailyStatistics(
            @Param("statisticsDate") LocalDate statisticsDate,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );

    /** 변경 탐색 범위에서 발견된 재집계 대상 날짜를 조회한다. */
    List<LocalDate> findChangedStatisticsDates(
            @Param("sourceWindowStartedAt") LocalDateTime sourceWindowStartedAt,
            @Param("sourceWindowEndedAt") LocalDateTime sourceWindowEndedAt,
            @Param("latestStatisticsDate") LocalDate latestStatisticsDate
    );

    /** 초기 백필의 주문 기준 시작일을 조회한다. */
    LocalDate findEarliestOrderDate();

    /** 초기 백필의 승인 결제 기준 시작일을 조회한다. */
    LocalDate findEarliestApprovedPaymentDate();
}
