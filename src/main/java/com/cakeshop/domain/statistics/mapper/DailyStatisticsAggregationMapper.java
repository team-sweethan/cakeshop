package com.cakeshop.domain.statistics.mapper;

import com.cakeshop.domain.statistics.dto.view.DailyStatisticsSourceView;
import java.time.LocalDate;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface DailyStatisticsAggregationMapper {

    /** 전달받은 집계값으로 해당 날짜의 확정 통계를 교체한다. */
    int upsertDailyStatistics(
            @Param("statisticsDate") LocalDate statisticsDate,
            @Param("source") DailyStatisticsSourceView source
    );
}
