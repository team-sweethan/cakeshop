package com.cakeshop.domain.statistics.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.cakeshop.domain.statistics.dto.form.StatisticsSearchForm;
import com.cakeshop.domain.statistics.dto.view.DailyStatisticsView;
import com.cakeshop.domain.statistics.dto.view.PeriodStatisticsView;
import com.cakeshop.domain.statistics.dto.view.StatisticsDashboardView;
import com.cakeshop.domain.statistics.error.StatisticsErrorCode;
import com.cakeshop.domain.statistics.service.DashboardReadModelQueryService;
import com.cakeshop.domain.statistics.service.PeriodStatisticsReadModelQueryService;
import com.cakeshop.global.error.BusinessException;
import java.math.BigDecimal;
import java.time.LocalDate;
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

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new StatisticsAdminController(
                        dashboardReadModelQueryService,
                        periodStatisticsReadModelQueryService
                ))
                .build();
    }

    @Test
    void dashboard_serviceReturnsView_addsDashboardToModel() throws Exception {
        StatisticsDashboardView dashboard =
                new StatisticsDashboardView(
                        3L,
                        new BigDecimal("120000"),
                        2L,
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
        when(periodStatisticsReadModelQueryService.getStatistics(startDate, endDate)).thenReturn(statistics);

        mockMvc.perform(get("/admin/statistics")
                        .param("startDate", startDate.toString())
                        .param("endDate", endDate.toString()))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/statistics"))
                .andExpect(model().attribute("statistics", statistics))
                .andExpect(model().attribute("searchForm", org.hamcrest.Matchers.allOf(
                        org.hamcrest.Matchers.hasProperty("startDate", org.hamcrest.Matchers.is(startDate)),
                        org.hamcrest.Matchers.hasProperty("endDate", org.hamcrest.Matchers.is(endDate))
                )));

        verify(periodStatisticsReadModelQueryService).getStatistics(startDate, endDate);
        verifyNoMoreInteractions(dashboardReadModelQueryService);
    }

    @Test
    void statistics_noPeriod_appliesResolvedDefaultPeriodToSearchForm() throws Exception {
        LocalDate startDate = LocalDate.of(2026, 8, 4);
        LocalDate endDate = LocalDate.of(2026, 8, 10);
        PeriodStatisticsView statistics = statistics(startDate, endDate);
        when(periodStatisticsReadModelQueryService.getStatistics(null, null)).thenReturn(statistics);

        mockMvc.perform(get("/admin/statistics"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/statistics"))
                .andExpect(model().attribute("statistics", statistics))
                .andExpect(model().attribute("searchForm", org.hamcrest.Matchers.allOf(
                        org.hamcrest.Matchers.hasProperty("startDate", org.hamcrest.Matchers.is(startDate)),
                        org.hamcrest.Matchers.hasProperty("endDate", org.hamcrest.Matchers.is(endDate))
                )));

        verify(periodStatisticsReadModelQueryService).getStatistics(null, null);
    }

    @Test
    void statistics_onlyStartDate_doesNotQueryStatistics() throws Exception {
        mockMvc.perform(get("/admin/statistics")
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
        when(periodStatisticsReadModelQueryService.getStatistics(startDate, endDate))
                .thenThrow(new BusinessException(StatisticsErrorCode.INVALID_DATE_RANGE));

        mockMvc.perform(get("/admin/statistics")
                        .param("startDate", startDate.toString())
                        .param("endDate", endDate.toString()))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/statistics"))
                .andExpect(model().attributeHasErrors("searchForm"))
                .andExpect(model().attributeDoesNotExist("statistics"));

        verify(periodStatisticsReadModelQueryService).getStatistics(startDate, endDate);
    }

    @Test
    void statistics_unaggregatedPeriod_addsNotReadyError() throws Exception {
        LocalDate startDate = LocalDate.of(2026, 8, 1);
        LocalDate endDate = LocalDate.of(2026, 8, 9);
        when(periodStatisticsReadModelQueryService.getLatestSelectableDate())
                .thenReturn(endDate);
        when(periodStatisticsReadModelQueryService.getStatistics(startDate, endDate))
                .thenThrow(new BusinessException(StatisticsErrorCode.STATISTICS_NOT_READY));

        mockMvc.perform(get("/admin/statistics")
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
                List.<DailyStatisticsView>of()
        );
    }
}
