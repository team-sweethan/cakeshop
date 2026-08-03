package com.cakeshop.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.time.LocalDate;

import com.cakeshop.domain.coupon.dto.form.CouponSearchCondition;
import com.cakeshop.domain.coupon.dto.view.CouponView;
import com.cakeshop.domain.product.service.ProductAdminService;
import com.cakeshop.global.common.paging.PageRequest;
import com.cakeshop.global.common.paging.PageResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.cakeshop.domain.community.controller.CommunityAdminController;
import com.cakeshop.domain.coupon.controller.CouponAdminController;
import com.cakeshop.domain.coupon.service.CouponAdminService;
import com.cakeshop.domain.member.controller.MemberAdminController;
import com.cakeshop.domain.member.service.MemberAdminService;
import com.cakeshop.domain.member.service.MemberSessionService;
import com.cakeshop.domain.notification.controller.NotificationAdminController;
import com.cakeshop.domain.order.controller.FulfillmentAdminController;
import com.cakeshop.domain.order.controller.OrderAdminController;
import com.cakeshop.domain.order.dto.view.FulfillmentListView;
import com.cakeshop.domain.order.service.OrderAdminService;
import com.cakeshop.domain.order.service.FulfillmentService;
import com.cakeshop.domain.payment.controller.PaymentAdminController;
import com.cakeshop.domain.payment.dto.view.PaymentAdminListView;
import com.cakeshop.domain.payment.dto.view.PaymentAdminSummaryView;
import com.cakeshop.domain.payment.service.PaymentAdminQueryService;
import com.cakeshop.domain.payment.service.RefundFacade;
import com.cakeshop.domain.product.controller.ProductAdminController;
import com.cakeshop.domain.review.controller.ReviewAdminController;
import com.cakeshop.domain.statistics.controller.StatisticsAdminController;

class AdminPageControllerTests {

    private MockMvc mockMvc;

    private final Map<String, String> pages = new LinkedHashMap<>();

    @BeforeEach
    void setUp() {

        CouponAdminService couponAdminService =
                Mockito.mock(CouponAdminService.class);

        // 관리자 공통 페이지 테스트에서 목록 컨트롤러가 페이징 값을 계산할 수 있도록 빈 결과를 반환한다.
        when(couponAdminService.getCoupons(
                any(CouponSearchCondition.class),
                any(PageRequest.class)
        )).thenReturn(new PageResult<CouponView>(
                List.of(),
                new PageRequest(null, null),
                0
        ));

        FulfillmentService fulfillmentService =
                Mockito.mock(FulfillmentService.class);
        when(fulfillmentService.getFulfillments(any()))
                .thenReturn(new FulfillmentListView(
                        LocalDate.of(2026, 8, 3),
                        null,
                        List.of()
                ));

        PaymentAdminQueryService paymentAdminQueryService =
                Mockito.mock(PaymentAdminQueryService.class);
        when(paymentAdminQueryService.getPayments(any()))
                .thenReturn(new PaymentAdminListView(
                        null,
                        new PaymentAdminSummaryView(0, 0, 0, 0),
                        List.of()
                ));

        mockMvc = MockMvcBuilders.standaloneSetup(
                new StatisticsAdminController(),
                new ProductAdminController(
                        Mockito.mock(ProductAdminService.class)),
                new OrderAdminController(
                        Mockito.mock(OrderAdminService.class),
                        Mockito.mock(RefundFacade.class)),
                new FulfillmentAdminController(
                        fulfillmentService),
                new PaymentAdminController(paymentAdminQueryService),
                new MemberAdminController(
                        Mockito.mock(MemberAdminService.class),
                        Mockito.mock(MemberSessionService.class)),
                new ReviewAdminController(),
                new NotificationAdminController(),
                new CommunityAdminController(),
                new CouponAdminController(couponAdminService)
        ).build();

        pages.put("/admin", "admin/dashboard");
        pages.put("/admin/statistics", "admin/statistics");
        pages.put("/admin/products", "admin/product/list");
        pages.put("/admin/products/new", "admin/product/form");
        pages.put("/admin/products/1/edit", "admin/product/form");
        pages.put("/admin/orders", "admin/order/list");
        pages.put("/admin/orders/1", "admin/order/detail");
        pages.put("/admin/fulfillment", "admin/fulfillment/list");
        pages.put("/admin/payments", "admin/payment/list");
        pages.put("/admin/members", "admin/member/list");
        pages.put("/admin/reviews", "admin/review/list");
        pages.put("/admin/notifications", "admin/notification/list");
        pages.put("/admin/community", "admin/community/list");
        pages.put("/admin/community/15", "admin/community/detail");
        pages.put("/admin/coupons", "admin/coupon/list");
        pages.put("/admin/coupons/create", "admin/coupon/form");
        pages.put("/admin/coupons/1/edit", "admin/coupon/form");
        pages.put("/admin/coupons/1/detail", "admin/coupon/detail");
    }

    @Test
    void everyAdminRouteReturnsItsTemplate() throws Exception {
        for (Map.Entry<String, String> page : pages.entrySet()) {
            mockMvc.perform(get(page.getKey()))
                .andExpect(status().isOk())
                .andExpect(view().name(page.getValue()));
        }
    }

    @Test
    void everyAdminViewHasAThymeleafTemplate() {
        pages.values().stream().distinct().forEach(viewName ->
            assertThat(new ClassPathResource("templates/" + viewName + ".html").exists())
                .as("%s 템플릿이 존재해야 한다", viewName)
                .isTrue()
        );
    }
}
