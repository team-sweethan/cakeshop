package com.cakeshop.domain.statistics.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.cakeshop.domain.statistics.dto.view.StatisticsDashboardView;
import com.cakeshop.domain.statistics.service.DashboardReadModelQueryService;
import java.math.BigDecimal;
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

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new StatisticsAdminController(dashboardReadModelQueryService))
                .build();
    }

    @Test
    void dashboard_serviceReturnsView_addsDashboardToModel() throws Exception {
        StatisticsDashboardView dashboard =
                new StatisticsDashboardView(3L, new BigDecimal("120000"), 2L);
        when(dashboardReadModelQueryService.getDashboard()).thenReturn(dashboard);

        mockMvc.perform(get("/admin"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/dashboard"))
                .andExpect(model().attribute("dashboard", dashboard));

        verify(dashboardReadModelQueryService).getDashboard();
    }

    @Test
    void statistics_request_doesNotQueryDashboard() throws Exception {
        mockMvc.perform(get("/admin/statistics"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/statistics"));

        verifyNoMoreInteractions(dashboardReadModelQueryService);
    }
}
