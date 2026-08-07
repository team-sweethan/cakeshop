package com.cakeshop.domain.statistics.mapper;

import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface DashboardReadModelMapper {

    /** 결제 승인 시각을 기준으로 현재 유효한 오늘 주문 수를 조회한다. */
    long countTodayOrders(
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );
}
