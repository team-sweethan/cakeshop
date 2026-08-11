package com.cakeshop.domain.statistics.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.cakeshop.domain.statistics.dto.view.PeriodStatisticsView;
import com.cakeshop.domain.statistics.service.DashboardReadModelQueryService;
import com.cakeshop.domain.statistics.service.PeriodStatisticsReadModelQueryService;
import com.cakeshop.global.security.SecurityConfig;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(StatisticsAdminController.class)
@Import(SecurityConfig.class)
class StatisticsAdminControllerSecurityTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DashboardReadModelQueryService dashboardReadModelQueryService;

    @MockitoBean
    private PeriodStatisticsReadModelQueryService periodStatisticsReadModelQueryService;

    @Test
    @WithAnonymousUser
    void statistics_notAuthenticated_redirectsToLogin() throws Exception {
        mockMvc.perform(get("/admin/statistics"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    @WithMockUser(roles = "USER")
    void statistics_customerRole_isForbidden() throws Exception {
        mockMvc.perform(get("/admin/statistics"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void statistics_adminRole_isAccessible() throws Exception {
        LocalDate startDate = LocalDate.of(2026, 8, 4);
        LocalDate endDate = LocalDate.of(2026, 8, 10);
        when(periodStatisticsReadModelQueryService.getStatistics(any()))
                .thenReturn(new PeriodStatisticsView(
                        startDate,
                        endDate,
                        0L,
                        0L,
                        0L,
                        BigDecimal.ZERO,
                        List.of()
                ));

        mockMvc.perform(get("/admin/statistics")
                        .param("startDate", startDate.toString())
                        .param("endDate", endDate.toString()))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/statistics"));
    }
}
