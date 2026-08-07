package com.cakeshop.domain.statistics.controller;

import com.cakeshop.domain.statistics.dto.view.StatisticsDashboardView;
import com.cakeshop.domain.statistics.service.DashboardReadModelQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@RequiredArgsConstructor
public class StatisticsAdminController {

    private final DashboardReadModelQueryService dashboardReadModelQueryService;

    @GetMapping("/admin")
    public String dashboard(Model model) {
        StatisticsDashboardView dashboard = dashboardReadModelQueryService.getDashboard();
        model.addAttribute("dashboard", dashboard);

        return "admin/dashboard";
    }

    @GetMapping("/admin/statistics")
    public String statistics() {
        return "admin/statistics";
    }
}
