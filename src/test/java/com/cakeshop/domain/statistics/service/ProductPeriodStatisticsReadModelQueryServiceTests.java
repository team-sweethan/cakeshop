package com.cakeshop.domain.statistics.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cakeshop.domain.statistics.dto.view.ProductStatisticsView;
import com.cakeshop.domain.statistics.error.StatisticsErrorCode;
import com.cakeshop.domain.statistics.mapper.PeriodStatisticsReadModelMapper;
import com.cakeshop.global.error.BusinessException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProductPeriodStatisticsReadModelQueryServiceTests {

    private static final LocalDate START_DATE = LocalDate.of(2026, 8, 1);
    private static final LocalDate END_DATE = LocalDate.of(2026, 8, 7);

    @Mock
    private PeriodStatisticsReadModelMapper mapper;

    @InjectMocks
    private ProductPeriodStatisticsReadModelQueryService service;

    @Test
    void getProductStatistics_completedRange_returnsAllProductRanks() {
        List<ProductStatisticsView> expected = List.of(new ProductStatisticsView(
                1,
                10,
                "상품 A",
                3,
                5,
                new BigDecimal("50000")
        ));
        when(mapper.countIncompleteProductStatisticsDates(START_DATE, END_DATE)).thenReturn(0);
        when(mapper.findProductStatistics(START_DATE, END_DATE)).thenReturn(expected);

        List<ProductStatisticsView> result = service.getProductStatistics(START_DATE, END_DATE);

        assertThat(result).isEqualTo(expected);
    }

    @Test
    void getProductStatistics_incompleteDateExists_rejectsPartialResult() {
        when(mapper.countIncompleteProductStatisticsDates(START_DATE, END_DATE)).thenReturn(1);

        assertThatThrownBy(() -> service.getProductStatistics(START_DATE, END_DATE))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(StatisticsErrorCode.PRODUCT_STATISTICS_NOT_READY)
                );
        verify(mapper, never()).findProductStatistics(START_DATE, END_DATE);
    }
}
