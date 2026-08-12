package com.cakeshop.domain.statistics.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cakeshop.domain.statistics.dto.view.PeriodStatisticsView;
import com.cakeshop.domain.statistics.dto.view.ProductStatisticsView;
import com.cakeshop.domain.statistics.dto.view.StatisticsTrendView;
import com.cakeshop.domain.statistics.error.StatisticsErrorCode;
import com.cakeshop.domain.statistics.service.DashboardReadModelQueryService;
import com.cakeshop.domain.statistics.service.PeriodStatisticsReadModelQueryService;
import com.cakeshop.domain.statistics.service.ProductPeriodStatisticsReadModelQueryService;
import com.cakeshop.global.error.BusinessException;
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

    @MockitoBean
    private ProductPeriodStatisticsReadModelQueryService productStatisticsQueryService;

    @Test
    @WithMockUser(roles = "ADMIN")
    void chartAsset_request_servesPinnedChartJs() throws Exception {
        mockMvc.perform(get("/webjars/chart.js/4.5.1/dist/chart.umd.js"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void statisticsCssAsset_request_isServed() throws Exception {
        mockMvc.perform(get("/css/statistics.css"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void statisticsPeriodAsset_request_isServed() throws Exception {
        mockMvc.perform(get("/js/statistics-period.js"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void statisticsProductRankingAsset_request_isServed() throws Exception {
        mockMvc.perform(get("/js/statistics-product-ranking.js"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("data-product-ranking-toggle")))
                .andExpect(content().string(containsString("aria-expanded")));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void statistics_statisticsReturned_rendersSummaryAndDailyTrend() throws Exception {
        LocalDate startDate = LocalDate.of(2026, 8, 8);
        LocalDate endDate = LocalDate.of(2026, 8, 9);
        when(periodStatisticsReadModelQueryService.getLatestSelectableDate())
                .thenReturn(endDate);
        when(periodStatisticsReadModelQueryService.getStatistics(any()))
                .thenReturn(new PeriodStatisticsView(
                        startDate,
                        endDate,
                        12L,
                        8L,
                        2L,
                        new BigDecimal("123456"),
                        List.of(
                                StatisticsTrendView.daily(
                                        startDate,
                                        5L,
                                        new BigDecimal("45678")
                                ),
                                StatisticsTrendView.daily(
                                        endDate,
                                        7L,
                                        new BigDecimal("77778")
                                )
                        )
                ));

        mockMvc.perform(get("/admin/statistics")
                        .param("periodType", "RANGE")
                        .param("startDate", startDate.toString())
                        .param("endDate", endDate.toString()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("value=\"2026-08-08\"")))
                .andExpect(content().string(containsString("max=\"2026-08-09\"")))
                .andExpect(content().string(containsString(
                        "통계는 집계가 완료된 어제까지 제공됩니다."
                )))
                .andExpect(content().string(containsString("12건")))
                .andExpect(content().string(containsString("123,456원")))
                .andExpect(content().string(containsString("일별 주문 추이")))
                .andExpect(content().string(containsString("일별 매출 추이")))
                .andExpect(content().string(containsString("id=\"statistics-order-chart\"")))
                .andExpect(content().string(containsString("id=\"statistics-sales-chart\"")))
                .andExpect(content().string(containsString("data-axis-label=\"2026.08.08\"")))
                .andExpect(content().string(containsString("data-order-count=\"5\"")))
                .andExpect(content().string(containsString("data-sales-amount=\"45678\"")))
                .andExpect(content().string(containsString("/webjars/chart.js/4.5.1/dist/chart.umd.js")))
                .andExpect(content().string(containsString("/css/statistics.css")))
                .andExpect(content().string(containsString("/js/statistics-period.js")))
                .andExpect(content().string(containsString("/js/statistics-chart.js")))
                .andExpect(content().string(not(containsString("data-date-label"))))
                .andExpect(content().string(not(containsString("data-statistics-metric"))))
                .andExpect(content().string(not(containsString("<table"))))
                .andExpect(content().string(not(containsString("data-product-ranking-toggle"))))
                .andExpect(content().string(not(containsString("딸기 생크림 케이크"))))
                .andExpect(content().string(not(containsString("주별"))));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void statistics_noCondition_rendersActiveRecentWeekBeforeWeeklyAndMonthly() throws Exception {
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

        mockMvc.perform(get("/admin/statistics"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.matchesPattern(
                        "(?s).*name=\"periodType\"\\s+value=\"RANGE\".*"
                )))
                .andExpect(content().string(containsString(
                        "href=\"/admin/statistics?periodType=RECENT_WEEK\""
                )))
                .andExpect(content().string(containsString("최근 일주일")))
                .andExpect(content().string(containsString("aria-current=\"page\"")))
                .andExpect(content().string(org.hamcrest.Matchers.matchesPattern(
                        "(?s).*최근 일주일</a>.*주간</a>.*월간</a>.*"
                )));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void statistics_productStatisticsReturned_rendersProductRankingTable() throws Exception {
        LocalDate startDate = LocalDate.of(2026, 8, 8);
        LocalDate endDate = LocalDate.of(2026, 8, 9);
        when(periodStatisticsReadModelQueryService.getStatistics(any()))
                .thenReturn(new PeriodStatisticsView(
                        startDate,
                        endDate,
                        4L,
                        2L,
                        0L,
                        new BigDecimal("62000"),
                        List.of()
                ));
        when(productStatisticsQueryService.getProductStatistics(startDate, endDate))
                .thenReturn(List.of(
                        new ProductStatisticsView(
                                1L,
                                6L,
                                "딸기 생크림 케이크",
                                3L,
                                5L,
                                new BigDecimal("50000")
                        ),
                        new ProductStatisticsView(
                                2L,
                                7L,
                                "초코 케이크",
                                1L,
                                2L,
                                new BigDecimal("12000")
                        )
                ));

        mockMvc.perform(get("/admin/statistics")
                        .param("periodType", "RANGE")
                        .param("startDate", startDate.toString())
                        .param("endDate", endDate.toString()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("상품별 주문 · 판매 · 매출 순위")))
                .andExpect(content().string(containsString("<table")))
                .andExpect(content().string(containsString("순위")))
                .andExpect(content().string(containsString("판매 수량")))
                .andExpect(content().string(containsString("딸기 생크림 케이크")))
                .andExpect(content().string(containsString("3건")))
                .andExpect(content().string(containsString("5개")))
                .andExpect(content().string(containsString("50,000원")))
                .andExpect(content().string(containsString("초코 케이크")))
                .andExpect(content().string(not(containsString("data-product-ranking-toggle"))));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void statistics_moreThanFiveProducts_hidesOverflowAndRendersToggle() throws Exception {
        LocalDate startDate = LocalDate.of(2026, 8, 8);
        LocalDate endDate = LocalDate.of(2026, 8, 9);
        when(periodStatisticsReadModelQueryService.getStatistics(any()))
                .thenReturn(new PeriodStatisticsView(
                        startDate,
                        endDate,
                        6L,
                        0L,
                        0L,
                        BigDecimal.ZERO,
                        List.of()
                ));
        when(productStatisticsQueryService.getProductStatistics(startDate, endDate))
                .thenReturn(java.util.stream.LongStream.rangeClosed(1, 6)
                        .mapToObj(ranking -> new ProductStatisticsView(
                                ranking,
                                ranking,
                                "상품 " + ranking,
                                1L,
                                1L,
                                BigDecimal.valueOf(7 - ranking)
                        ))
                        .toList());

        mockMvc.perform(get("/admin/statistics")
                        .param("periodType", "RANGE")
                        .param("startDate", startDate.toString())
                        .param("endDate", endDate.toString()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("상품 5")))
                .andExpect(content().string(containsString("상품 6")))
                .andExpect(content().string(containsString(
                        "data-product-ranking-overflow=\"true\""
                )))
                .andExpect(content().string(containsString("hidden")))
                .andExpect(content().string(containsString("data-product-ranking-toggle")))
                .andExpect(content().string(containsString("aria-expanded=\"false\"")))
                .andExpect(content().string(containsString("전체보기")))
                .andExpect(content().string(containsString(
                        "/js/statistics-product-ranking.js"
                )));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void statistics_productStatisticsNotReady_rendersMainStatisticsAndNotice() throws Exception {
        LocalDate startDate = LocalDate.of(2026, 8, 8);
        LocalDate endDate = LocalDate.of(2026, 8, 9);
        when(periodStatisticsReadModelQueryService.getStatistics(any()))
                .thenReturn(new PeriodStatisticsView(
                        startDate,
                        endDate,
                        12L,
                        8L,
                        2L,
                        new BigDecimal("123456"),
                        List.of()
                ));
        when(productStatisticsQueryService.getProductStatistics(startDate, endDate))
                .thenThrow(new BusinessException(
                        StatisticsErrorCode.PRODUCT_STATISTICS_NOT_READY
                ));

        mockMvc.perform(get("/admin/statistics")
                        .param("periodType", "RANGE")
                        .param("startDate", startDate.toString())
                        .param("endDate", endDate.toString()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("12건")))
                .andExpect(content().string(containsString("123,456원")))
                .andExpect(content().string(containsString("상품별 주문 · 판매 · 매출 순위")))
                .andExpect(content().string(containsString(
                        StatisticsErrorCode.PRODUCT_STATISTICS_NOT_READY.message()
                )))
                .andExpect(content().string(not(containsString("<table"))))
                .andExpect(content().string(not(containsString("data-product-ranking-toggle"))));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void statistics_weeklySearch_rendersActiveButtonAndResolvedWeek() throws Exception {
        LocalDate startDate = LocalDate.of(2026, 8, 3);
        LocalDate endDate = LocalDate.of(2026, 8, 9);
        when(periodStatisticsReadModelQueryService.getLatestSelectableDate())
                .thenReturn(LocalDate.of(2026, 8, 10));
        when(periodStatisticsReadModelQueryService.getStatistics(any()))
                .thenReturn(new PeriodStatisticsView(
                        startDate,
                        endDate,
                        0,
                        0,
                        0,
                        BigDecimal.ZERO,
                        List.of()
                ));

        mockMvc.perform(get("/admin/statistics")
                        .param("periodType", "WEEKLY"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("aria-current=\"page\"")))
                .andExpect(content().string(containsString("name=\"week\"")))
                .andExpect(content().string(containsString("type=\"week\"")))
                .andExpect(content().string(containsString("value=\"2026-W32\"")))
                .andExpect(content().string(containsString("일별 주문 추이")))
                .andExpect(content().string(not(containsString("주차별 주문 추이"))));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void statistics_monthlySearch_rendersMonthAndWeeklyTrend() throws Exception {
        LocalDate startDate = LocalDate.of(2026, 7, 1);
        LocalDate endDate = LocalDate.of(2026, 7, 31);
        when(periodStatisticsReadModelQueryService.getLatestSelectableDate())
                .thenReturn(LocalDate.of(2026, 8, 10));
        when(periodStatisticsReadModelQueryService.getStatistics(any()))
                .thenReturn(new PeriodStatisticsView(
                        startDate,
                        endDate,
                        0,
                        0,
                        0,
                        BigDecimal.ZERO,
                        List.of(new StatisticsTrendView(
                                "1주차 (07.01~07.05)",
                                startDate,
                                LocalDate.of(2026, 7, 5),
                                0,
                                BigDecimal.ZERO
                        ))
                ));

        mockMvc.perform(get("/admin/statistics")
                        .param("periodType", "MONTHLY"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("name=\"yearMonth\"")))
                .andExpect(content().string(containsString("value=\"2026-07\"")))
                .andExpect(content().string(containsString("주차별 주문 추이")))
                .andExpect(content().string(containsString("주차별 매출 추이")))
                .andExpect(content().string(containsString(
                        "data-axis-label=\"1주차 (07.01~07.05)\""
                )));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void statistics_aggregationDelayed_rendersLatestCompletedDateNotice() throws Exception {
        LocalDate startDate = LocalDate.of(2026, 8, 2);
        LocalDate endDate = LocalDate.of(2026, 8, 8);
        when(periodStatisticsReadModelQueryService.getLatestSelectableDate())
                .thenReturn(LocalDate.of(2026, 8, 9));
        when(periodStatisticsReadModelQueryService.getStatistics(any()))
                .thenReturn(new PeriodStatisticsView(
                        startDate,
                        endDate,
                        0,
                        0,
                        0,
                        BigDecimal.ZERO,
                        List.of(),
                        true
                ));

        mockMvc.perform(get("/admin/statistics"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("전날 집계가 아직 완료되지 않아")))
                .andExpect(content().string(containsString("2026.08.08")));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void statistics_onlyStartDate_rendersValidationMessageWithoutStatistics() throws Exception {
        mockMvc.perform(get("/admin/statistics")
                        .param("periodType", "RANGE")
                        .param("startDate", "2026-08-09"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("시작일과 종료일을 모두 입력해 주세요.")))
                .andExpect(content().string(not(containsString("총 주문 건수"))));

        verifyNoInteractions(periodStatisticsReadModelQueryService);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void statistics_blankPeriodType_rendersValidationMessageWithoutStatistics() throws Exception {
        mockMvc.perform(get("/admin/statistics")
                        .param("periodType", ""))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("조회 유형을 선택해 주세요.")))
                .andExpect(content().string(not(containsString("총 주문 건수"))));

        verifyNoInteractions(periodStatisticsReadModelQueryService);
    }
}
