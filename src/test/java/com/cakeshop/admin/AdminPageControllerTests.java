package com.cakeshop.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.cakeshop.domain.community.controller.CommunityAdminController;
import com.cakeshop.domain.coupon.controller.CouponAdminController;
import com.cakeshop.domain.member.controller.MemberAdminController;
import com.cakeshop.domain.notification.controller.NotificationAdminController;
import com.cakeshop.domain.order.controller.FulfillmentAdminController;
import com.cakeshop.domain.order.controller.OrderAdminController;
import com.cakeshop.domain.payment.controller.PaymentAdminController;
import com.cakeshop.domain.product.admin.controller.ProductAdminController;
import com.cakeshop.domain.product.admin.dto.view.ProductAdminListView;
import com.cakeshop.domain.product.admin.service.ProductAdminService;
import com.cakeshop.domain.review.controller.ReviewAdminController;
import com.cakeshop.domain.statistics.controller.StatisticsAdminController;
import com.cakeshop.global.common.paging.PageRequest;
import com.cakeshop.global.common.paging.PageResult;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AdminPageControllerTests {

    private MockMvc mockMvc;

    private final Map<String, String> pages =
            new LinkedHashMap<>();

    @BeforeEach
    void setUp() {
        // ProductAdminController에 전달할 Service mock을 만든다.
        ProductAdminService productAdminService =
                mock(ProductAdminService.class);

        // 관리자 상품 목록 요청에 사용할 빈 페이지 결과를 만든다.
        PageResult<ProductAdminListView> pageResult =
                new PageResult<>(
                        List.of(),
                        new PageRequest(1, 10),
                        0
                );

        // 관리자 상품 목록을 조회하면 빈 페이지를 반환하도록 설정한다.
        when(productAdminService.getProducts(any()))
                .thenReturn(pageResult);

        // 테스트할 관리자 Controller들을 등록한다.
        mockMvc = MockMvcBuilders
                .standaloneSetup(
                        new StatisticsAdminController(),
                        new ProductAdminController(
                                productAdminService
                        ),
                        new OrderAdminController(),
                        new FulfillmentAdminController(),
                        new PaymentAdminController(),
                        new CouponAdminController(),
                        new MemberAdminController(),
                        new ReviewAdminController(),
                        new NotificationAdminController(),
                        new CommunityAdminController()
                )
                .build();

        // 관리자 URL과 반환되어야 하는 템플릿을 연결한다.
        pages.put(
                "/admin",
                "admin/dashboard"
        );
        pages.put(
                "/admin/statistics",
                "admin/statistics"
        );
        pages.put(
                "/admin/products",
                "admin/product/list"
        );
        pages.put(
                "/admin/products/new",
                "admin/product/form"
        );
        pages.put(
                "/admin/products/1/edit",
                "admin/product/form"
        );
        pages.put(
                "/admin/orders",
                "admin/order/list"
        );
        pages.put(
                "/admin/orders/1",
                "admin/order/detail"
        );
        pages.put(
                "/admin/fulfillment",
                "admin/fulfillment/list"
        );
        pages.put(
                "/admin/payments",
                "admin/payment/list"
        );
        pages.put(
                "/admin/coupons",
                "admin/coupon/list"
        );
        pages.put(
                "/admin/members",
                "admin/member/list"
        );
        pages.put(
                "/admin/reviews",
                "admin/review/list"
        );
        pages.put(
                "/admin/notifications",
                "admin/notification/list"
        );
        pages.put(
                "/admin/community",
                "admin/community/list"
        );
        pages.put(
                "/admin/community/15",
                "admin/community/detail"
        );
    }

    @Test
    void everyAdminRouteReturnsItsTemplate()
            throws Exception {
        // 모든 관리자 URL이 예상한 템플릿을 반환하는지 확인한다.
        for (Map.Entry<String, String> page
                : pages.entrySet()) {
            mockMvc.perform(get(page.getKey()))
                    .andExpect(status().isOk())
                    .andExpect(
                            view().name(page.getValue())
                    );
        }
    }

    @Test
    void everyAdminViewHasAThymeleafTemplate() {
        // Controller가 반환하는 모든 Thymeleaf 파일이 존재하는지 확인한다.
        pages.values()
                .stream()
                .distinct()
                .forEach(viewName ->
                        assertThat(
                                new ClassPathResource(
                                        "templates/"
                                                + viewName
                                                + ".html"
                                ).exists()
                        )
                                .as(
                                        "%s 템플릿이 존재해야 한다",
                                        viewName
                                )
                                .isTrue()
                );
    }
}