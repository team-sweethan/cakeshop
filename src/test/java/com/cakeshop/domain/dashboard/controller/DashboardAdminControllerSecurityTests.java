package com.cakeshop.domain.dashboard.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.cakeshop.domain.dashboard.dto.view.DashboardView;
import com.cakeshop.domain.dashboard.service.DashboardReadModelQueryService;
import com.cakeshop.global.security.SecurityConfig;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(DashboardAdminController.class)
@Import(SecurityConfig.class)
class DashboardAdminControllerSecurityTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DashboardReadModelQueryService dashboardReadModelQueryService;

    @Test
    @WithAnonymousUser
    void dashboard_notAuthenticated_redirectsToLogin() throws Exception {
        mockMvc.perform(get("/admin"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/login"));
    }

    @Test
    @WithMockUser(roles = "USER")
    void dashboard_customerRole_isForbidden() throws Exception {
        mockMvc.perform(get("/admin"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void dashboard_adminRole_isAccessible() throws Exception {
        when(dashboardReadModelQueryService.getDashboard()).thenReturn(emptyDashboard());

        mockMvc.perform(get("/admin"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/dashboard"));
    }

    private DashboardView emptyDashboard() {
        return new DashboardView(
                0L,
                BigDecimal.ZERO,
                0L,
                0L,
                0L,
                0L,
                0L,
                0L,
                List.of(),
                List.of(),
                List.of()
        );
    }
}
