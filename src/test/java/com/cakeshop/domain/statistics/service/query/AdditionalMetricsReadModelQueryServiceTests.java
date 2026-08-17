package com.cakeshop.domain.statistics.service.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cakeshop.domain.statistics.dto.view.AdditionalMetricsView;
import com.cakeshop.domain.statistics.error.StatisticsErrorCode;
import com.cakeshop.domain.statistics.mapper.PeriodStatisticsReadModelMapper;
import com.cakeshop.global.error.BusinessException;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdditionalMetricsReadModelQueryServiceTests {

    private static final LocalDate START_DATE = LocalDate.of(2026, 8, 1);
    private static final LocalDate END_DATE = LocalDate.of(2026, 8, 7);

    @Mock
    private PeriodStatisticsReadModelMapper mapper;

    @InjectMocks
    private AdditionalMetricsReadModelQueryService service;

    @Test
    void getAdditionalMetrics_completedRange_returnsMetrics() {
        AdditionalMetricsView expected = new AdditionalMetricsView(
                5,
                1,
                8,
                3,
                new BigDecimal("12000"),
                new BigDecimal("25000")
        );
        when(mapper.countIncompleteAdditionalMetricsDates(START_DATE, END_DATE)).thenReturn(0);
        when(mapper.findAdditionalMetrics(START_DATE, END_DATE)).thenReturn(expected);

        AdditionalMetricsView result = service.getAdditionalMetrics(START_DATE, END_DATE);

        assertThat(result).isEqualTo(expected);
    }

    @Test
    void getAdditionalMetrics_incompleteDateExists_rejectsPartialResult() {
        when(mapper.countIncompleteAdditionalMetricsDates(START_DATE, END_DATE)).thenReturn(1);

        assertThatThrownBy(() -> service.getAdditionalMetrics(START_DATE, END_DATE))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(StatisticsErrorCode.ADDITIONAL_METRICS_NOT_READY)
                );
        verify(mapper, never()).findAdditionalMetrics(START_DATE, END_DATE);
    }
}
