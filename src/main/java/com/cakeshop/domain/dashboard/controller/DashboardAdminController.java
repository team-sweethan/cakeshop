package com.cakeshop.domain.dashboard.controller;

import com.cakeshop.domain.dashboard.dto.view.DashboardView;
import com.cakeshop.domain.dashboard.service.DashboardReadModelQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@RequiredArgsConstructor
public class DashboardAdminController {

    private final DashboardReadModelQueryService dashboardReadModelQueryService;

    /** 관리자 대시보드의 현재 운영 지표를 조회한다. */
    @GetMapping("/admin")
    public String dashboard(Model model) {
        DashboardView dashboard = dashboardReadModelQueryService.getDashboard();
        model.addAttribute("dashboard", dashboard);

        return "admin/dashboard";
    }
}
