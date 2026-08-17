package com.cakeshop.domain.dashboard.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.cakeshop.domain.dashboard.dto.view.DashboardView;
import com.cakeshop.domain.dashboard.service.DashboardReadModelQueryService;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class DashboardAdminControllerTests {

    @Mock
    private DashboardReadModelQueryService dashboardReadModelQueryService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new DashboardAdminController(dashboardReadModelQueryService))
                .build();
    }

    @Test
    void dashboard_serviceReturnsView_addsDashboardToModel() throws Exception {
        DashboardView dashboard = new DashboardView(
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
}
