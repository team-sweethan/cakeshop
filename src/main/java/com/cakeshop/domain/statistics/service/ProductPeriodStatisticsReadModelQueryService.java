package com.cakeshop.domain.statistics.service;

import com.cakeshop.domain.statistics.dto.view.ProductStatisticsView;
import com.cakeshop.domain.statistics.error.StatisticsErrorCode;
import com.cakeshop.domain.statistics.mapper.PeriodStatisticsReadModelMapper;
import com.cakeshop.global.error.BusinessException;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 조회 기간의 상품별 통계와 매출 순위를 조회한다. */
@Service
@RequiredArgsConstructor
public class ProductPeriodStatisticsReadModelQueryService {

    private final PeriodStatisticsReadModelMapper mapper;

    /** 상품별 집계 완료를 확인한 뒤 전체 상품 순위를 조회한다. */
    @Transactional(readOnly = true)
    public List<ProductStatisticsView> getProductStatistics(
            LocalDate startDate,
            LocalDate endDate
    ) {
        if (mapper.countIncompleteProductStatisticsDates(startDate, endDate) > 0) {
            throw new BusinessException(StatisticsErrorCode.PRODUCT_STATISTICS_NOT_READY);
        }
        return mapper.findProductStatistics(startDate, endDate);
    }
}
