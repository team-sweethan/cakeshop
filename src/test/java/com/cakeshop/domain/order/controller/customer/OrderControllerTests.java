package com.cakeshop.domain.order.controller.customer;

import com.cakeshop.domain.member.dto.view.MemberAuthenticationView;
import com.cakeshop.domain.member.dto.view.MemberProfileView;
import com.cakeshop.domain.member.service.MemberService;
import com.cakeshop.domain.coupon.service.CouponOrderQueryService;
import com.cakeshop.domain.order.dto.view.customer.GeneralOrderCheckoutView;
import com.cakeshop.domain.order.service.customer.OrderCheckoutService;
import com.cakeshop.domain.order.controller.customer.OrderController;
import com.cakeshop.domain.order.service.customer.CustomerOrderQueryService;
import com.cakeshop.domain.order.service.OrderService;
import com.cakeshop.domain.payment.service.RefundFacade;
import com.cakeshop.global.security.MemberDetails;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.List;
import java.time.LocalDate;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasProperty;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@ExtendWith(MockitoExtension.class)
class OrderControllerTests {

    @Mock
    private OrderCheckoutService orderCheckoutService;

    @Mock
    private OrderService orderService;

    @Mock
    private CustomerOrderQueryService orderQueryService;

    @Mock
    private MemberService memberService;

    @Mock
    private RefundFacade refundFacade;

    @Mock
    private CouponOrderQueryService couponOrderQueryService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new OrderController(
                                orderCheckoutService,
                                orderService,
                                orderQueryService,
                                memberService,
                                refundFacade,
                                couponOrderQueryService
                        )
                )
                .setCustomArgumentResolvers(
                        new AuthenticationPrincipalArgumentResolver()
                )
                .build();

        MemberDetails principal = new MemberDetails(
                new MemberAuthenticationView(
                        10L,
                        "member@example.com",
                        "password",
                        "USER",
                        true
                )
        );
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        principal,
                        principal.getPassword(),
                        principal.getAuthorities()
                )
        );
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void checkout_validSelection_addsActualCheckoutToModel() throws Exception {
        GeneralOrderCheckoutView checkout = mock(GeneralOrderCheckoutView.class);
        when(checkout.totalAmount()).thenReturn(BigDecimal.valueOf(40_000));
        when(memberService.getMemberProfile("member@example.com"))
                .thenReturn(new MemberProfileView(
                        "member@example.com",
                        "홍길동",
                        "케이크러버",
                        "010-1111-2222",
                        LocalDate.of(2000, 1, 1)
                ));
        when(orderCheckoutService.getGeneralCheckout(
                1L,
                2,
                List.of(101L, 102L)
        )).thenReturn(checkout);

        mockMvc.perform(get("/orders/checkout")
                        .param("productId", "1")
                        .param("quantity", "2")
                        .param("optionIds", "101", "102")
                        .param("pickupAt", "2099-08-05T14:00"))
                .andExpect(status().isOk())
                .andExpect(view().name("customer/order/form"))
                .andExpect(model().attribute("checkout", checkout))
                .andExpect(model().attribute("orderForm", allOf(
                        hasProperty("ordererName", equalTo("홍길동")),
                        hasProperty("ordererPhone", equalTo("010-1111-2222")),
                        hasProperty("pickupName", equalTo("홍길동")),
                        hasProperty("pickupPhone", equalTo("010-1111-2222")),
                        hasProperty("requestKey", org.hamcrest.Matchers.matchesPattern(
                                "^[0-9a-f-]{36}$"
                        )),
                        hasProperty("displayedOriginalAmount", equalTo(BigDecimal.valueOf(40_000)))
                )));

        verify(orderCheckoutService).getGeneralCheckout(
                1L,
                2,
                List.of(101L, 102L)
        );
    }

    @Test
    void createGeneralOrder_validRequest_redirectsWithCreatedOrderId()
            throws Exception {
        String requestKey = UUID.randomUUID().toString();
        when(orderService.createGeneralOrder(eq(10L), argThat(form ->
                form.getProductId().equals(1L)
                        && form.getQuantity().equals(2)
                        && form.getOptionIds().equals(List.of(101L))
        ))).thenReturn(42L);

        mockMvc.perform(post("/orders/general")
                        .param("requestKey", requestKey)
                        .param("productId", "1")
                        .param("quantity", "2")
                        .param("optionIds", "101")
                        .param("displayedOriginalAmount", "40000")
                        .param("ordererName", "홍길동")
                        .param("ordererPhone", "010-1111-2222")
                        .param("pickupName", "홍길동")
                        .param("pickupPhone", "010-1111-2222")
                        .param("pickupAt", "2099-08-05T14:00")
                        .param("requestMessage", "초는 빼주세요"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/orders/42/payment"));

        verify(orderService).createGeneralOrder(
                eq(10L),
                argThat(form ->
                        form.getPickupAt() != null
                                && requestKey.equals(form.getRequestKey())
                                && BigDecimal.valueOf(40_000).compareTo(form.getDisplayedOriginalAmount()) == 0
                                && "홍길동".equals(form.getPickupName())
                                && "초는 빼주세요".equals(form.getRequestMessage())
                )
        );
    }

    @Test
    void createGeneralOrder_changedDisplayedAmount_rendersUpdatedOrderForm() throws Exception {
        GeneralOrderCheckoutView checkout = mock(GeneralOrderCheckoutView.class);
        when(checkout.totalAmount()).thenReturn(BigDecimal.valueOf(40_000));
        when(orderCheckoutService.getGeneralCheckout(1L, 2, List.of(101L))).thenReturn(checkout);
        when(orderService.createGeneralOrder(eq(10L), any()))
                .thenThrow(new com.cakeshop.global.error.BusinessException(
                        com.cakeshop.domain.order.error.OrderErrorCode.ORDER_AMOUNT_CHANGED
                ));

        mockMvc.perform(post("/orders/general")
                        .param("requestKey", UUID.randomUUID().toString())
                        .param("productId", "1")
                        .param("quantity", "2")
                        .param("optionIds", "101")
                        .param("displayedOriginalAmount", "30000")
                        .param("ordererName", "홍길동")
                        .param("ordererPhone", "010-1111-2222")
                        .param("pickupName", "홍길동")
                        .param("pickupPhone", "010-1111-2222")
                        .param("pickupAt", "2099-08-05T14:00"))
                .andExpect(status().isOk())
                .andExpect(view().name("customer/order/form"))
                .andExpect(model().attribute("checkout", checkout))
                .andExpect(model().attributeHasErrors("orderForm"));
    }

    @Test
    void customOptions_returnsExistingMockupPage() throws Exception {
        mockMvc.perform(get("/orders/custom/options"))
                .andExpect(status().isOk())
                .andExpect(view().name("customer/order/custom-option"));
    }

    @Test
    void customRequest_returnsExistingMockupPage() throws Exception {
        mockMvc.perform(get("/orders/custom/request"))
                .andExpect(status().isOk())
                .andExpect(view().name("customer/order/custom-request"));
    }
}
