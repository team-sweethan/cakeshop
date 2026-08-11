package com.cakeshop.domain.statistics.mapper;

import com.cakeshop.domain.statistics.dto.view.DailyStatisticsRow;
import com.cakeshop.domain.statistics.dto.view.ProductStatisticsView;
import java.time.LocalDate;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface PeriodStatisticsReadModelMapper {

    /** 조회 기간에 집계가 완료된 날짜별 주문·매출 통계를 조회한다. */
    List<DailyStatisticsRow> findDailyStatistics(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    /** 조회 기간에 상품별 집계가 완료되지 않은 날짜 수를 조회한다. */
    int countIncompleteProductStatisticsDates(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    /** 조회 기간의 상품별 주문·판매·매출 합계와 매출 순위를 조회한다. */
    List<ProductStatisticsView> findProductStatistics(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    /** 백필 시작일부터 끊기지 않고 집계된 마지막 날짜를 조회한다. */
    LocalDate findLatestContinuousStatisticsDate(
            @Param("latestStatisticsDate") LocalDate latestStatisticsDate
    );
}
