package com.cakeshop.domain.statistics.mapper;

import com.cakeshop.domain.statistics.dto.view.DailyStatisticsSourceView;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface DailyStatisticsSourceReadModelMapper {

    /** 원본 주문·결제에서 해당 날짜의 통계 집계값을 조회한다. */
    DailyStatisticsSourceView findDailyStatistics(
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
