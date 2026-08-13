package com.cakeshop.domain.statistics.query;

import com.cakeshop.domain.statistics.dto.view.AdditionalMetricsView;
import com.cakeshop.domain.statistics.error.StatisticsErrorCode;
import com.cakeshop.domain.statistics.mapper.PeriodStatisticsReadModelMapper;
import com.cakeshop.global.error.BusinessException;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 조회 기간의 활동·금액 지표를 조회한다. */
@Service
@RequiredArgsConstructor
public class AdditionalMetricsReadModelQueryService {

    private final PeriodStatisticsReadModelMapper mapper;

    /** 기타 지표 집계 완료를 확인한 뒤 활동·금액 지표를 조회한다. */
    @Transactional(readOnly = true)
    public AdditionalMetricsView getAdditionalMetrics(
            LocalDate startDate,
            LocalDate endDate
    ) {
        if (mapper.countIncompleteAdditionalMetricsDates(startDate, endDate) > 0) {
            throw new BusinessException(StatisticsErrorCode.ADDITIONAL_METRICS_NOT_READY);
        }
        return mapper.findAdditionalMetrics(startDate, endDate);
    }
}
