package com.cakeshop.domain.statistics.controller;

import com.cakeshop.domain.statistics.dto.form.StatisticsPeriodType;
import com.cakeshop.domain.statistics.dto.form.StatisticsSearchForm;
import com.cakeshop.domain.statistics.dto.view.PeriodStatisticsView;
import com.cakeshop.domain.statistics.error.StatisticsErrorCode;
import com.cakeshop.domain.statistics.service.query.AdditionalMetricsReadModelQueryService;
import com.cakeshop.domain.statistics.service.query.PeriodStatisticsReadModelQueryService;
import com.cakeshop.domain.statistics.service.query.ProductPeriodStatisticsReadModelQueryService;
import com.cakeshop.global.error.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.IsoFields;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;

@Controller
@RequiredArgsConstructor
public class StatisticsAdminController {

    private static final DateTimeFormatter WEEK_FORMATTER = new DateTimeFormatterBuilder()
            .appendValue(IsoFields.WEEK_BASED_YEAR, 4)
            .appendLiteral("-W")
            .appendValue(IsoFields.WEEK_OF_WEEK_BASED_YEAR, 2)
            .toFormatter();

    private final PeriodStatisticsReadModelQueryService periodStatisticsReadModelQueryService;
    private final ProductPeriodStatisticsReadModelQueryService productStatisticsQueryService;
    private final AdditionalMetricsReadModelQueryService additionalMetricsQueryService;

    /** 조회 유형이 생략된 기존 날짜 URL은 직접 기간 조회로 호환한다. */
    @ModelAttribute("searchForm")
    public StatisticsSearchForm searchForm(HttpServletRequest request) {
        StatisticsSearchForm searchForm = new StatisticsSearchForm();
        boolean periodTypeOmitted = !request.getParameterMap().containsKey("periodType");
        boolean dateRangeProvided = request.getParameterMap().containsKey("startDate")
                || request.getParameterMap().containsKey("endDate");
        if (periodTypeOmitted && dateRangeProvided) {
            searchForm.setPeriodType(StatisticsPeriodType.RANGE);
        }
        return searchForm;
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
            if (searchForm.getPeriodType() == StatisticsPeriodType.WEEKLY
                    && searchForm.getWeek() == null) {
                searchForm.setWeek(statistics.startDate().format(WEEK_FORMATTER));
            }
            if (searchForm.getPeriodType() == StatisticsPeriodType.MONTHLY
                    && searchForm.getYearMonth() == null) {
                searchForm.setYearMonth(YearMonth.from(statistics.startDate()));
            }
            model.addAttribute("statistics", statistics);
            addProductStatistics(model, statistics);
            addAdditionalMetrics(model, statistics);
        } catch (BusinessException e) {
            if (e.getErrorCode() != StatisticsErrorCode.INVALID_DATE_RANGE
                    && e.getErrorCode() != StatisticsErrorCode.STATISTICS_NOT_READY) {
                throw e;
            }
            bindingResult.reject(e.getErrorCode().code(), e.getMessage());
        }

        return "admin/statistics";
    }

    private void addProductStatistics(Model model, PeriodStatisticsView statistics) {
        try {
            model.addAttribute(
                    "productStatistics",
                    productStatisticsQueryService.getProductStatistics(
                            statistics.startDate(),
                            statistics.endDate()
                    )
            );
        } catch (BusinessException e) {
            if (e.getErrorCode() != StatisticsErrorCode.PRODUCT_STATISTICS_NOT_READY) {
                throw e;
            }
            model.addAttribute("productStatisticsError", e.getMessage());
        }
    }

    private void addAdditionalMetrics(Model model, PeriodStatisticsView statistics) {
        try {
            model.addAttribute(
                    "additionalMetrics",
                    additionalMetricsQueryService.getAdditionalMetrics(
                            statistics.startDate(),
                            statistics.endDate()
                    )
            );
        } catch (BusinessException e) {
            if (e.getErrorCode() != StatisticsErrorCode.ADDITIONAL_METRICS_NOT_READY) {
                throw e;
            }
            model.addAttribute("additionalMetricsError", e.getMessage());
        }
    }
}
