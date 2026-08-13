package com.cakeshop.domain.statistics.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.cakeshop.domain.statistics.dto.form.StatisticsPeriodType;
import com.cakeshop.domain.statistics.dto.form.StatisticsSearchForm;
import com.cakeshop.domain.statistics.dto.view.AdditionalMetricsView;
import com.cakeshop.domain.statistics.dto.view.PeriodStatisticsView;
import com.cakeshop.domain.statistics.dto.view.ProductStatisticsView;
import com.cakeshop.domain.statistics.dto.view.StatisticsTrendView;
import com.cakeshop.domain.dashboard.dto.view.DashboardView;
import com.cakeshop.domain.statistics.error.StatisticsErrorCode;
import com.cakeshop.domain.statistics.service.AdditionalMetricsReadModelQueryService;
import com.cakeshop.domain.statistics.service.DashboardReadModelQueryService;
import com.cakeshop.domain.statistics.service.PeriodStatisticsReadModelQueryService;
import com.cakeshop.domain.statistics.service.ProductPeriodStatisticsReadModelQueryService;
import com.cakeshop.global.error.BusinessException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class StatisticsAdminControllerTests {

    @Mock
    private DashboardReadModelQueryService dashboardReadModelQueryService;

    @Mock
    private PeriodStatisticsReadModelQueryService periodStatisticsReadModelQueryService;

    @Mock
    private ProductPeriodStatisticsReadModelQueryService productStatisticsQueryService;

    @Mock
    private AdditionalMetricsReadModelQueryService additionalMetricsQueryService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new StatisticsAdminController(
                        dashboardReadModelQueryService,
                        periodStatisticsReadModelQueryService,
                        productStatisticsQueryService,
                        additionalMetricsQueryService
                ))
                .build();
    }

    @Test
    void dashboard_serviceReturnsView_addsDashboardToModel() throws Exception {
        DashboardView dashboard =
                new DashboardView(
                        3L,
                        new BigDecimal("120000"),
                        2L,
                        0L,
                        0L,
                        0L,
                        0L,
                        0L,
                        List.of(),
                        List.of(),
                        List.of()
                );
        when(dashboardReadModelQueryService.getDashboard()).thenReturn(dashboard);

        mockMvc.perform(get("/admin"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/dashboard"))
                .andExpect(model().attribute("dashboard", dashboard));

        verify(dashboardReadModelQueryService).getDashboard();
    }

    @Test
    void statistics_validPeriod_addsStatisticsAndSearchFormToModel() throws Exception {
        LocalDate startDate = LocalDate.of(2026, 8, 1);
        LocalDate endDate = LocalDate.of(2026, 8, 10);
        PeriodStatisticsView statistics = statistics(startDate, endDate);
        List<ProductStatisticsView> productStatistics = List.of(
                new ProductStatisticsView(
                        1L,
                        6L,
                        "딸기 생크림 케이크",
                        3L,
                        5L,
                        new BigDecimal("50000")
                )
        );
        AdditionalMetricsView additionalMetrics = additionalMetrics();
        when(periodStatisticsReadModelQueryService.getStatistics(any())).thenReturn(statistics);
        when(productStatisticsQueryService.getProductStatistics(startDate, endDate))
                .thenReturn(productStatistics);
        when(additionalMetricsQueryService.getAdditionalMetrics(startDate, endDate))
                .thenReturn(additionalMetrics);

        mockMvc.perform(get("/admin/statistics")
                        .param("periodType", "RANGE")
                        .param("startDate", startDate.toString())
                        .param("endDate", endDate.toString()))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/statistics"))
                .andExpect(model().attribute("statistics", statistics))
                .andExpect(model().attribute("productStatistics", productStatistics))
                .andExpect(model().attribute("additionalMetrics", additionalMetrics))
                .andExpect(model().attribute("searchForm", org.hamcrest.Matchers.allOf(
                        org.hamcrest.Matchers.hasProperty("startDate", org.hamcrest.Matchers.is(startDate)),
                        org.hamcrest.Matchers.hasProperty("endDate", org.hamcrest.Matchers.is(endDate))
                )));

        verify(periodStatisticsReadModelQueryService).getStatistics(any());
        verify(productStatisticsQueryService).getProductStatistics(startDate, endDate);
        verify(additionalMetricsQueryService).getAdditionalMetrics(startDate, endDate);
        verifyNoMoreInteractions(dashboardReadModelQueryService);
    }

    @Test
    void statistics_dateRangeWithoutPeriodType_usesRangeForExistingUrlCompatibility()
            throws Exception {
        LocalDate startDate = LocalDate.of(2026, 8, 1);
        LocalDate endDate = LocalDate.of(2026, 8, 10);
        when(periodStatisticsReadModelQueryService.getStatistics(any()))
                .thenReturn(statistics(startDate, endDate));

        mockMvc.perform(get("/admin/statistics")
                        .param("startDate", startDate.toString())
                        .param("endDate", endDate.toString()))
                .andExpect(status().isOk())
                .andExpect(model().attribute("searchForm", org.hamcrest.Matchers.hasProperty(
                        "periodType",
                        org.hamcrest.Matchers.is(StatisticsPeriodType.RANGE)
                )));

        verify(periodStatisticsReadModelQueryService).getStatistics(argThat(form ->
                form.getPeriodType() == StatisticsPeriodType.RANGE
                        && startDate.equals(form.getStartDate())
                        && endDate.equals(form.getEndDate())
        ));
    }

    @Test
    void statistics_productStatisticsNotReady_keepsPeriodStatisticsInModel() throws Exception {
        LocalDate startDate = LocalDate.of(2026, 8, 1);
        LocalDate endDate = LocalDate.of(2026, 8, 10);
        PeriodStatisticsView statistics = statistics(startDate, endDate);
        when(periodStatisticsReadModelQueryService.getStatistics(any())).thenReturn(statistics);
        when(productStatisticsQueryService.getProductStatistics(startDate, endDate))
                .thenThrow(new BusinessException(
                        StatisticsErrorCode.PRODUCT_STATISTICS_NOT_READY
                ));

        mockMvc.perform(get("/admin/statistics")
                        .param("periodType", "RANGE")
                        .param("startDate", startDate.toString())
                        .param("endDate", endDate.toString()))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/statistics"))
                .andExpect(model().attribute("statistics", statistics))
                .andExpect(model().attribute(
                        "productStatisticsError",
                        StatisticsErrorCode.PRODUCT_STATISTICS_NOT_READY.message()
                ))
                .andExpect(model().attributeDoesNotExist("productStatistics"));
    }

    @Test
    void statistics_additionalMetricsNotReady_keepsOtherStatisticsInModel() throws Exception {
        LocalDate startDate = LocalDate.of(2026, 8, 1);
        LocalDate endDate = LocalDate.of(2026, 8, 10);
        PeriodStatisticsView statistics = statistics(startDate, endDate);
        when(periodStatisticsReadModelQueryService.getStatistics(any())).thenReturn(statistics);
        when(additionalMetricsQueryService.getAdditionalMetrics(startDate, endDate))
                .thenThrow(new BusinessException(
                        StatisticsErrorCode.ADDITIONAL_METRICS_NOT_READY
                ));

        mockMvc.perform(get("/admin/statistics")
                        .param("periodType", "RANGE")
                        .param("startDate", startDate.toString())
                        .param("endDate", endDate.toString()))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/statistics"))
                .andExpect(model().attribute("statistics", statistics))
                .andExpect(model().attribute(
                        "additionalMetricsError",
                        StatisticsErrorCode.ADDITIONAL_METRICS_NOT_READY.message()
                ))
                .andExpect(model().attributeDoesNotExist("additionalMetrics"));
    }

    @Test
    void statistics_noPeriod_appliesResolvedDefaultPeriodToSearchForm() throws Exception {
        LocalDate startDate = LocalDate.of(2026, 8, 4);
        LocalDate endDate = LocalDate.of(2026, 8, 10);
        PeriodStatisticsView statistics = statistics(startDate, endDate);
        when(periodStatisticsReadModelQueryService.getStatistics(any())).thenReturn(statistics);

        mockMvc.perform(get("/admin/statistics"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/statistics"))
                .andExpect(model().attribute("statistics", statistics))
                .andExpect(model().attribute("searchForm", org.hamcrest.Matchers.allOf(
                        org.hamcrest.Matchers.hasProperty(
                                "periodType",
                                org.hamcrest.Matchers.is(StatisticsPeriodType.RECENT_WEEK)
                        ),
                        org.hamcrest.Matchers.hasProperty("startDate", org.hamcrest.Matchers.is(startDate)),
                        org.hamcrest.Matchers.hasProperty("endDate", org.hamcrest.Matchers.is(endDate))
                )));

        verify(periodStatisticsReadModelQueryService).getStatistics(any());
    }

    @Test
    void statistics_recentWeekRequest_bindsRecentWeek() throws Exception {
        LocalDate startDate = LocalDate.of(2026, 8, 4);
        LocalDate endDate = LocalDate.of(2026, 8, 10);
        when(periodStatisticsReadModelQueryService.getStatistics(any()))
                .thenReturn(statistics(startDate, endDate));

        mockMvc.perform(get("/admin/statistics")
                        .param("periodType", "RECENT_WEEK"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("searchForm", org.hamcrest.Matchers.hasProperty(
                        "periodType",
                        org.hamcrest.Matchers.is(StatisticsPeriodType.RECENT_WEEK)
                )));

        verify(periodStatisticsReadModelQueryService).getStatistics(argThat(form ->
                form.getPeriodType() == StatisticsPeriodType.RECENT_WEEK
        ));
    }

    @Test
    void statistics_weeklyRequest_bindsWeek() throws Exception {
        String week = "2026-W31";
        PeriodStatisticsView statistics = statistics(
                LocalDate.of(2026, 7, 27),
                LocalDate.of(2026, 8, 2)
        );
        when(periodStatisticsReadModelQueryService.getStatistics(any())).thenReturn(statistics);

        mockMvc.perform(get("/admin/statistics")
                        .param("periodType", "WEEKLY")
                        .param("week", week))
                .andExpect(status().isOk())
                .andExpect(model().attribute("searchForm", org.hamcrest.Matchers.allOf(
                        org.hamcrest.Matchers.hasProperty(
                                "periodType",
                                org.hamcrest.Matchers.is(StatisticsPeriodType.WEEKLY)
                        ),
                        org.hamcrest.Matchers.hasProperty(
                                "week",
                                org.hamcrest.Matchers.is(week)
                        )
                )));

        verify(periodStatisticsReadModelQueryService).getStatistics(argThat(form ->
                form.getPeriodType() == StatisticsPeriodType.WEEKLY
                        && week.equals(form.getWeek())
        ));
    }

    @Test
    void statistics_weeklyRequestWithBlankWeek_appliesResolvedDefaultWeek() throws Exception {
        PeriodStatisticsView statistics = statistics(
                LocalDate.of(2026, 8, 3),
                LocalDate.of(2026, 8, 9)
        );
        when(periodStatisticsReadModelQueryService.getStatistics(any())).thenReturn(statistics);

        mockMvc.perform(get("/admin/statistics")
                        .param("periodType", "WEEKLY")
                        .param("week", ""))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasNoErrors("searchForm"))
                .andExpect(model().attribute("searchForm", org.hamcrest.Matchers.allOf(
                        org.hamcrest.Matchers.hasProperty(
                                "periodType",
                                org.hamcrest.Matchers.is(StatisticsPeriodType.WEEKLY)
                        ),
                        org.hamcrest.Matchers.hasProperty(
                                "week",
                                org.hamcrest.Matchers.is("2026-W32")
                        )
                )));

        verify(periodStatisticsReadModelQueryService).getStatistics(any());
    }

    @Test
    void statistics_monthlyRequest_bindsYearMonth() throws Exception {
        YearMonth yearMonth = YearMonth.of(2026, 7);
        PeriodStatisticsView statistics = statistics(
                yearMonth.atDay(1),
                yearMonth.atEndOfMonth()
        );
        when(periodStatisticsReadModelQueryService.getStatistics(any())).thenReturn(statistics);

        mockMvc.perform(get("/admin/statistics")
                        .param("periodType", "MONTHLY")
                        .param("yearMonth", yearMonth.toString()))
                .andExpect(status().isOk())
                .andExpect(model().attribute("searchForm", org.hamcrest.Matchers.allOf(
                        org.hamcrest.Matchers.hasProperty(
                                "periodType",
                                org.hamcrest.Matchers.is(StatisticsPeriodType.MONTHLY)
                        ),
                        org.hamcrest.Matchers.hasProperty(
                                "yearMonth",
                                org.hamcrest.Matchers.is(yearMonth)
                        )
                )));

        verify(periodStatisticsReadModelQueryService).getStatistics(argThat(form ->
                form.getPeriodType() == StatisticsPeriodType.MONTHLY
                        && yearMonth.equals(form.getYearMonth())
        ));
    }

    @Test
    void statistics_unknownPeriodType_doesNotQueryStatistics() throws Exception {
        mockMvc.perform(get("/admin/statistics")
                        .param("periodType", "UNKNOWN"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/statistics"))
                .andExpect(model().attributeHasFieldErrors("searchForm", "periodType"))
                .andExpect(model().attributeDoesNotExist("statistics"));

        verifyNoInteractions(periodStatisticsReadModelQueryService);
    }

    @Test
    void statistics_onlyStartDate_doesNotQueryStatistics() throws Exception {
        mockMvc.perform(get("/admin/statistics")
                        .param("periodType", "RANGE")
                        .param("startDate", "2026-08-01"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/statistics"))
                .andExpect(model().attributeHasErrors("searchForm"))
                .andExpect(model().attributeDoesNotExist("statistics"));

        verifyNoInteractions(periodStatisticsReadModelQueryService);
    }

    @Test
    void statistics_futureEndDate_addsServiceValidationError() throws Exception {
        LocalDate startDate = LocalDate.of(2026, 8, 10);
        LocalDate endDate = LocalDate.of(2026, 8, 11);
        when(periodStatisticsReadModelQueryService.getStatistics(any()))
                .thenThrow(new BusinessException(StatisticsErrorCode.INVALID_DATE_RANGE));

        mockMvc.perform(get("/admin/statistics")
                        .param("periodType", "RANGE")
                        .param("startDate", startDate.toString())
                        .param("endDate", endDate.toString()))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/statistics"))
                .andExpect(model().attributeHasErrors("searchForm"))
                .andExpect(model().attributeDoesNotExist("statistics"));

        verify(periodStatisticsReadModelQueryService).getStatistics(any());
    }

    @Test
    void statistics_unaggregatedPeriod_addsNotReadyError() throws Exception {
        LocalDate startDate = LocalDate.of(2026, 8, 1);
        LocalDate endDate = LocalDate.of(2026, 8, 9);
        when(periodStatisticsReadModelQueryService.getLatestSelectableDate())
                .thenReturn(endDate);
        when(periodStatisticsReadModelQueryService.getStatistics(any()))
                .thenThrow(new BusinessException(StatisticsErrorCode.STATISTICS_NOT_READY));

        mockMvc.perform(get("/admin/statistics")
                        .param("periodType", "RANGE")
                        .param("startDate", startDate.toString())
                        .param("endDate", endDate.toString()))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/statistics"))
                .andExpect(model().attributeHasErrors("searchForm"))
                .andExpect(model().attributeDoesNotExist("statistics"));
    }

    private PeriodStatisticsView statistics(LocalDate startDate, LocalDate endDate) {
        return new PeriodStatisticsView(
                startDate,
                endDate,
                0,
                0,
                0,
                BigDecimal.ZERO,
                List.<StatisticsTrendView>of()
        );
    }

    private AdditionalMetricsView additionalMetrics() {
        return new AdditionalMetricsView(
                5,
                1,
                8,
                3,
                new BigDecimal("12000"),
                new BigDecimal("25000")
        );
    }
}
