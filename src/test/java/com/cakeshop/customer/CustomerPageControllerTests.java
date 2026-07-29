package com.cakeshop.customer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.cakeshop.domain.cart.controller.CartController;
import com.cakeshop.domain.coupon.controller.CouponController;
import com.cakeshop.domain.home.controller.HomeController;
import com.cakeshop.domain.home.service.HomeService;
import com.cakeshop.domain.member.controller.AuthController;
import com.cakeshop.domain.member.dto.view.MemberAuthenticationView;
import com.cakeshop.domain.member.dto.view.MemberProfileView;
import com.cakeshop.domain.member.controller.MyPageController;
import com.cakeshop.domain.member.service.MemberService;
import com.cakeshop.domain.notification.controller.NotificationController;
import com.cakeshop.domain.order.controller.OrderController;
import com.cakeshop.domain.payment.controller.PaymentController;
import com.cakeshop.domain.product.customer.controller.ProductController;
import com.cakeshop.domain.product.customer.service.ProductService;
import com.cakeshop.domain.review.controller.ReviewController;
import com.cakeshop.global.security.MemberDetails;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class CustomerPageControllerTests {

    private MockMvc mockMvc;
    private final Map<String, String> pages = new LinkedHashMap<>();

    @BeforeEach
    void setUp() {
        MemberDetails principal = new MemberDetails(new MemberAuthenticationView(
                1L,
                "customer@cakeshop.local",
                "dummy",
                "CUSTOMER",
                true));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        principal, null, principal.getAuthorities()));

        MemberService memberService = mock(MemberService.class);
        when(memberService.getMemberProfile(anyString()))
                .thenReturn(new MemberProfileView(
                        "customer@cakeshop.local",
                        "고객",
                        "케이크러버",
                        "010-1234-5678"));

        mockMvc = MockMvcBuilders.standaloneSetup(
                        new HomeController(mock(HomeService.class)),
                        new AuthController(memberService),
                        new ProductController(mock(ProductService.class)),
                        new CartController(),
                        new OrderController(),
                        new PaymentController(),
                        new MyPageController(
                                memberService,
                                mock(SessionRegistry.class)),
                        new NotificationController(),
                        new CouponController(),
                        new ReviewController())
                .setCustomArgumentResolvers(
                        new AuthenticationPrincipalArgumentResolver())
                .build();

        pages.put("/screens", "home/screens");
        pages.put("/login", "auth/login");
        pages.put("/signup", "customer/member/signup");
        pages.put("/products", "customer/product/list");
        pages.put("/products/1", "customer/product/detail");
        pages.put("/cart", "customer/cart/list");
        pages.put("/orders/pickup", "customer/order/pickup-setting");
        pages.put("/orders/custom/options", "customer/order/custom-option");
        pages.put("/orders/custom/request", "customer/order/custom-request");
        pages.put("/orders/checkout", "customer/order/form");
        pages.put("/orders/1/payment", "customer/payment/form");
        pages.put("/orders/complete", "customer/order/complete");
        pages.put("/mypage", "customer/member/mypage");
        pages.put("/orders/1", "customer/order/detail");
        pages.put("/notifications", "customer/notification/list");
        pages.put("/reviews/new", "customer/review/form");
        pages.put("/mypage/coupons", "customer/coupon/list");
        pages.put("/mypage/profile", "customer/member/profile-edit");
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void everyCustomerRouteReturnsItsTemplate() throws Exception {
        for (Map.Entry<String, String> page : pages.entrySet()) {
            mockMvc.perform(get(page.getKey()))
                    .andExpect(status().isOk())
                    .andExpect(view().name(page.getValue()));
        }
    }

    @Test
    void everyCustomerViewAndMockupAssetExists() {
        pages.values().stream().distinct().forEach(viewName ->
                assertThat(
                        new ClassPathResource("templates/" + viewName + ".html").exists())
                        .as("%s template must exist", viewName)
                        .isTrue()
        );
        assertThat(
                new ClassPathResource("static/css/customer-mockup.css").exists())
                .isTrue();
        assertThat(
                new ClassPathResource("static/js/customer-mockup.js").exists())
                .isTrue();
    }

    @Test
    void importedCartMockupUsesSpringRoutesAndIncludesItsBehavior()
            throws IOException {
        String cartTemplate =
                new ClassPathResource("templates/customer/cart/list.html")
                        .getContentAsString(StandardCharsets.UTF_8);
        String mockupScript =
                new ClassPathResource("static/js/customer-mockup.js")
                        .getContentAsString(StandardCharsets.UTF_8);

        assertThat(cartTemplate)
                .contains("data-cart-root", "href=\"/products\"");
        assertThat(mockupScript)
                .contains("source: cakeProjectSample/js/cart.js")
                .contains("location.href = \"/cart\"")
                .contains("event.preventDefault()")
                .doesNotContain("event.preventDefalt()")
                .doesNotContain("/customer/");
    }
}
