package com.cakeshop.domain.dashboard.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cakeshop.domain.dashboard.dto.view.DashboardView;
import com.cakeshop.domain.dashboard.dto.view.PickupUrgency;
import com.cakeshop.domain.dashboard.dto.view.TodayPickupScheduleView;
import com.cakeshop.domain.dashboard.service.DashboardReadModelQueryService;
import com.cakeshop.global.security.SecurityConfig;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(DashboardAdminController.class)
@Import(SecurityConfig.class)
class DashboardAdminScreenRenderingTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DashboardReadModelQueryService dashboardReadModelQueryService;

    @Test
    @WithMockUser(roles = "ADMIN")
    void dashboard_metrics_rendersCountsAndManagementLinks() throws Exception {
        when(dashboardReadModelQueryService.getDashboard())
                .thenReturn(new DashboardView(
                        LocalDateTime.of(2026, 8, 17, 12, 0),
                        0,
                        BigDecimal.ZERO,
                        3,
                        0,
                        4,
                        0,
                        0,
                        5,
                        List.of(
                                new TodayPickupScheduleView(
                                        1L,
                                        LocalDateTime.of(2026, 8, 17, 11, 0),
                                        "ORDER-1",
                                        "지연 상품",
                                        PickupUrgency.OVERDUE
                                ),
                                new TodayPickupScheduleView(
                                        2L,
                                        LocalDateTime.of(2026, 8, 17, 12, 30),
                                        "ORDER-2",
                                        "임박 상품",
                                        PickupUrgency.IMMINENT
                                ),
                                new TodayPickupScheduleView(
                                        3L,
                                        LocalDateTime.of(2026, 8, 17, 14, 0),
                                        "ORDER-3",
                                        "예정 상품",
                                        PickupUrgency.SCHEDULED
                                )
                        ),
                        List.of(),
                        List.of()
                ));

        mockMvc.perform(get("/admin"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("조회 기준")))
                .andExpect(content().string(containsString("2026.08.17 12:00:00")))
                .andExpect(content().string(containsString("href=\"/admin\">새로고침</a>")))
                .andExpect(content().string(containsString("<strong>3건</strong>")))
                .andExpect(content().string(containsString("<strong>4건</strong>")))
                .andExpect(content().string(containsString("<strong>5건</strong>")))
                .andExpect(content().string(containsString("<th>픽업 상태</th>")))
                .andExpect(content().string(containsString(
                        "class=\"badge badge--danger\">지연</span>"
                )))
                .andExpect(content().string(containsString(
                        "class=\"badge badge--warning\">임박</span>"
                )))
                .andExpect(content().string(containsString(
                        "class=\"badge badge--success\">예정</span>"
                )))
                .andExpect(content().string(containsString(
                        "href=\"/admin/fulfillment?status=UNDER_REVIEW\""
                )))
                .andExpect(content().string(containsString(
                        "href=\"/admin/fulfillment?status=READY_FOR_PICKUP\""
                )))
                .andExpect(content().string(containsString("픽업 대기 주문 전체 보기")))
                .andExpect(content().string(not(containsString(
                        "href=\"/admin/fulfillment?status=IN_PRODUCTION\""
                ))))
                .andExpect(content().string(containsString(
                        "href=\"/admin/community?sort=REPORTS\""
                )))
                .andExpect(content().string(containsString("신고 처리 대기 게시글")))
                .andExpect(content().string(not(containsString("신고 처리 대기 후기"))))
                .andExpect(content().string(not(containsString("구현 예정"))));
    }
}
