package com.cakeshop.domain.statistics.controller;

import com.cakeshop.domain.statistics.dto.form.StatisticsSearchForm;
import com.cakeshop.domain.statistics.dto.view.PeriodStatisticsView;
import com.cakeshop.domain.statistics.dto.view.StatisticsDashboardView;
import com.cakeshop.domain.statistics.error.StatisticsErrorCode;
import com.cakeshop.domain.statistics.service.DashboardReadModelQueryService;
import com.cakeshop.domain.statistics.service.PeriodStatisticsReadModelQueryService;
import com.cakeshop.global.error.BusinessException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;

@Controller
@RequiredArgsConstructor
public class StatisticsAdminController {

    private final DashboardReadModelQueryService dashboardReadModelQueryService;
    private final PeriodStatisticsReadModelQueryService periodStatisticsReadModelQueryService;

    /** 관리자 대시보드의 오늘 통계를 조회한다. */
    @GetMapping("/admin")
    public String dashboard(Model model) {
        StatisticsDashboardView dashboard = dashboardReadModelQueryService.getDashboard();
        model.addAttribute("dashboard", dashboard);

        return "admin/dashboard";
    }

    /** 조회 기간의 관리자 통계를 조회한다. */
    @GetMapping("/admin/statistics")
    public String statistics(
            @Valid @ModelAttribute("searchForm") StatisticsSearchForm searchForm,
            BindingResult bindingResult,
            Model model
    ) {
        if (bindingResult.hasErrors()) {
            return "admin/statistics";
        }

        model.addAttribute(
                "latestSelectableDate",
                periodStatisticsReadModelQueryService.getLatestSelectableDate()
        );
        try {
            PeriodStatisticsView statistics = periodStatisticsReadModelQueryService.getStatistics(
                    searchForm
            );
            searchForm.setStartDate(statistics.startDate());
            searchForm.setEndDate(statistics.endDate());
            model.addAttribute("statistics", statistics);
        } catch (BusinessException e) {
            if (e.getErrorCode() != StatisticsErrorCode.INVALID_DATE_RANGE
                    && e.getErrorCode() != StatisticsErrorCode.STATISTICS_NOT_READY) {
                throw e;
            }
            bindingResult.reject(e.getErrorCode().code(), e.getMessage());
        }

        return "admin/statistics";
    }
}
