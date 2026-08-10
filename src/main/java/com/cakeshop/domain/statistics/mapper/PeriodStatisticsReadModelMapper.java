package com.cakeshop.domain.statistics.mapper;

import com.cakeshop.domain.statistics.dto.view.DailyOrderStatisticsView;
import com.cakeshop.domain.statistics.dto.view.DailySalesStatisticsView;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface PeriodStatisticsReadModelMapper {

    /** 주문 생성일별 총 주문, 완료 주문과 취소 주문 건수를 조회한다. */
    List<DailyOrderStatisticsView> findDailyOrderStatistics(
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );

    /** 결제 승인일별 현재 유효한 매출 합계를 조회한다. */
    List<DailySalesStatisticsView> findDailySalesStatistics(
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );
}
