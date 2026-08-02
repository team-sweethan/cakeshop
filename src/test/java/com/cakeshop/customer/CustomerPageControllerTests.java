package com.cakeshop.customer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.cakeshop.domain.cart.controller.CartController;
import com.cakeshop.domain.cart.dto.view.CartView;
import com.cakeshop.domain.cart.service.CartService;
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
import com.cakeshop.domain.product.controller.ProductController;
import com.cakeshop.domain.product.service.ProductService;
import com.cakeshop.domain.review.controller.ReviewController;
import com.cakeshop.global.security.MemberDetails;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.List;
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
                        "010-1234-5678",
                        LocalDate.of(2000, 1, 15)));

        CartService cartService = mock(CartService.class);
        when(cartService.getCart(1L)).thenReturn(new CartView(
                List.of(), 0, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO));

        mockMvc = MockMvcBuilders.standaloneSetup(
                        new HomeController(mock(HomeService.class)),
                        new AuthController(memberService),
                        new ProductController(mock(ProductService.class)),
                        new CartController(cartService),
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
        pages.put("/find-email", "customer/member/find-email");
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
    void cartViewUsesServerBackedSpringRoutes()
            throws IOException {
        String cartTemplate =
                new ClassPathResource("templates/customer/cart/list.html")
                        .getContentAsString(StandardCharsets.UTF_8);

        assertThat(cartTemplate)
                .contains("th:each=\"item : ${cart.items}\"")
                .contains("th:action=\"@{/cart/items/{id}/quantity(id=${item.id})}\"")
                .contains("th:action=\"@{/cart/items/delete-selected}\"")
                .contains("th:action=\"@{/cart/items/delete-all}\"")
                .doesNotContain("data-cart-root");
    }

    @Test
    void customOrderViewDoesNotUseBrowserLocalCart() throws IOException {
        String customOrderTemplate =
                new ClassPathResource("templates/customer/order/custom-option.html")
                        .getContentAsString(StandardCharsets.UTF_8);

        assertThat(customOrderTemplate)
                .contains("장바구니 연동 준비 중")
                .doesNotContain("data-add-custom-cart");
    }

    @Test
    void pickupViewUsesDedicatedInitializationMarker() throws IOException {
        String pickupTemplate =
                new ClassPathResource("templates/customer/order/pickup-setting.html")
                        .getContentAsString(StandardCharsets.UTF_8);
        String mockupScript =
                new ClassPathResource("static/js/customer-mockup.js")
                        .getContentAsString(StandardCharsets.UTF_8);

        assertThat(pickupTemplate).contains("data-pickup-root");
        assertThat(mockupScript)
                .contains("document.querySelector(\"[data-pickup-root]\")")
                .doesNotContain(
                        "if (!document.querySelector(\"[data-add-normal-cart]\")) return;");
    }

    @Test
    void productDetailSeparatesGeneralCartAndCustomOptionFlows() throws IOException {
        String productDetailTemplate =
                new ClassPathResource("templates/customer/product/detail.html")
                        .getContentAsString(StandardCharsets.UTF_8);

        assertThat(productDetailTemplate)
                .contains("and product.productType.name() == 'GENERAL'")
                .contains("th:href=\"@{/orders/custom/options}\"");
    }

    @Test
    void cartAsyncUpdateRefreshesEveryAffectedItemAvailability() throws IOException {
        String cartScript = new ClassPathResource("static/js/cart.js")
                .getContentAsString(StandardCharsets.UTF_8);
        String cartTemplate =
                new ClassPathResource("templates/customer/cart/list.html")
                        .getContentAsString(StandardCharsets.UTF_8);

        assertThat(cartScript)
                .contains("cart.itemAvailability.forEach")
                .contains("state.available");
        assertThat(cartTemplate)
                .contains("item.stockQuantity != null ? item.stockQuantity : 10");
    }
}
