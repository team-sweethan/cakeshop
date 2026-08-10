package com.cakeshop.domain.statistics.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cakeshop.domain.statistics.dto.view.DailyStatisticsView;
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
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(StatisticsAdminController.class)
@Import(SecurityConfig.class)
class StatisticsAdminScreenRenderingTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DashboardReadModelQueryService dashboardReadModelQueryService;

    @MockitoBean
    private PeriodStatisticsReadModelQueryService periodStatisticsReadModelQueryService;

    @Test
    @WithMockUser(roles = "ADMIN")
    void statistics_statisticsReturned_rendersSummaryAndDailyTrend() throws Exception {
        LocalDate startDate = LocalDate.of(2026, 8, 9);
        LocalDate endDate = LocalDate.of(2026, 8, 10);
        when(periodStatisticsReadModelQueryService.getStatistics(startDate, endDate))
                .thenReturn(new PeriodStatisticsView(
                        startDate,
                        endDate,
                        12L,
                        8L,
                        2L,
                        new BigDecimal("123456"),
                        List.of(
                                new DailyStatisticsView(
                                        startDate,
                                        5L,
                                        new BigDecimal("45678")
                                ),
                                new DailyStatisticsView(
                                        endDate,
                                        7L,
                                        new BigDecimal("77778")
                                )
                        )
                ));

        mockMvc.perform(get("/admin/statistics")
                        .param("startDate", startDate.toString())
                        .param("endDate", endDate.toString()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("value=\"2026-08-09\"")))
                .andExpect(content().string(containsString("12건")))
                .andExpect(content().string(containsString("123,456원")))
                .andExpect(content().string(containsString("2026.08.09")))
                .andExpect(content().string(containsString("45,678원")))
                .andExpect(content().string(not(containsString("딸기 생크림 케이크"))))
                .andExpect(content().string(not(containsString("주별"))));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void statistics_onlyStartDate_rendersValidationMessageWithoutStatistics() throws Exception {
        mockMvc.perform(get("/admin/statistics")
                        .param("startDate", "2026-08-09"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("시작일과 종료일을 모두 입력해 주세요.")))
                .andExpect(content().string(not(containsString("총 주문 건수"))));

        verifyNoInteractions(periodStatisticsReadModelQueryService);
    }
}
