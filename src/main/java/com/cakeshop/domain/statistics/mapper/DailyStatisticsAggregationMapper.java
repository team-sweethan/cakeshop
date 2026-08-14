package com.cakeshop.domain.statistics.mapper;

import com.cakeshop.domain.statistics.dto.source.DailyAdditionalMetricsSourceView;
import com.cakeshop.domain.statistics.dto.source.DailyProductStatisticsSourceView;
import com.cakeshop.domain.statistics.dto.source.DailyStatisticsSourceView;
import java.time.LocalDate;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface DailyStatisticsAggregationMapper {

    /** 전달받은 집계값으로 해당 날짜의 확정 통계를 교체한다. */
    int upsertDailyStatistics(
            @Param("statisticsDate") LocalDate statisticsDate,
            @Param("source") DailyStatisticsSourceView source
    );

    /** 전달받은 집계값으로 해당 날짜의 활동·금액 지표와 완료 시각을 교체한다. */
    int updateDailyAdditionalMetrics(
            @Param("statisticsDate") LocalDate statisticsDate,
            @Param("source") DailyAdditionalMetricsSourceView source
    );

    /** 해당 날짜의 기존 상품별 통계를 삭제한다. */
    int deleteDailyProductStatistics(@Param("statisticsDate") LocalDate statisticsDate);

    /** 전달받은 집계값으로 해당 날짜의 상품별 통계를 저장한다. */
    int insertDailyProductStatistics(
            @Param("statisticsDate") LocalDate statisticsDate,
            @Param("sources") List<DailyProductStatisticsSourceView> sources
    );

    /** 해당 날짜의 상품별 통계 집계 완료 시각을 기록한다. */
    int updateProductAggregatedAt(@Param("statisticsDate") LocalDate statisticsDate);
}
