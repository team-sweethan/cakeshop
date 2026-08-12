package com.cakeshop.domain.statistics.service;

import com.cakeshop.domain.statistics.dto.view.DailyProductStatisticsSourceView;
import com.cakeshop.domain.statistics.mapper.DailyStatisticsSourceReadModelMapper;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 상품별 일별 통계에 필요한 원본 집계값을 검증하여 조회한다. */
@Service
@RequiredArgsConstructor
public class DailyProductStatisticsSourceReadModelQueryService {

    private final DailyStatisticsSourceReadModelMapper sourceReadModelMapper;

    /** 원본 주문 금액과 주문 항목 합계를 검증한 뒤 상품별 집계값을 조회한다. */
    @Transactional(readOnly = true)
    public List<DailyProductStatisticsSourceView> getDailyProductStatistics(
            LocalDateTime start,
            LocalDateTime end
    ) {
        if (sourceReadModelMapper.existsInvalidProductSalesAllocationOrder(start, end)) {
            throw new IllegalStateException("상품별 매출을 배분할 수 없는 주문 데이터입니다.");
        }
        return sourceReadModelMapper.findDailyProductStatistics(start, end);
    }
}
