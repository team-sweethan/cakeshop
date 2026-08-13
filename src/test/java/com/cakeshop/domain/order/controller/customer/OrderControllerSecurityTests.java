package com.cakeshop.domain.order.controller.customer;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cakeshop.domain.member.dto.view.MemberAuthenticationView;
import com.cakeshop.domain.member.service.MemberService;
import com.cakeshop.domain.cart.service.CartOrderQueryService;
import com.cakeshop.domain.coupon.service.CouponOrderQueryService;
import com.cakeshop.domain.order.service.OrderService;
import com.cakeshop.domain.order.service.customer.CustomerCustomOrderService;
import com.cakeshop.domain.order.service.customer.OrderCustomerService;
import com.cakeshop.domain.order.service.customer.OrderCheckoutService;
import com.cakeshop.domain.payment.service.RefundFacade;
import com.cakeshop.domain.product.service.ProductQueryService;
import com.cakeshop.global.security.MemberDetails;
import com.cakeshop.global.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
        controllers = OrderController.class,
        properties = "app.mockup.public-preview=true"
)
@Import(SecurityConfig.class)
class OrderControllerSecurityTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RefundFacade refundFacade;

    @MockitoBean
    private OrderCheckoutService orderCheckoutService;

    @MockitoBean
    private OrderService orderService;

    @MockitoBean
    private OrderCustomerService orderQueryService;

    @MockitoBean
    private MemberService memberService;

    @MockitoBean
    private CouponOrderQueryService couponOrderQueryService;

    @MockitoBean
    private CustomerCustomOrderService customerCustomOrderService;

    @MockitoBean
    private ProductQueryService productQueryService;

    @MockitoBean
    private CartOrderQueryService cartOrderQueryService;

    @Test
    void checkout_anonymousUser_redirectsToLoginEvenInPublicPreview() throws Exception {
        mockMvc.perform(get("/orders/checkout"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void cartCheckout_anonymousUser_redirectsToLoginEvenInPublicPreview() throws Exception {
        mockMvc.perform(get("/orders/checkout/cart").param("itemIds", "1"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void customOptions_anonymousUser_redirectsToLoginEvenInPublicPreview() throws Exception {
        mockMvc.perform(get("/orders/custom/options"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void cancel_missingCsrf_isForbidden() throws Exception {
        mockMvc.perform(post("/orders/10/cancel")
                        .with(authentication(memberAuthentication()))
                        .param("reason", "단순 변심"))
                .andExpect(status().isForbidden());
    }

    @Test
    void cancel_authenticatedMemberWithCsrf_usesPrincipalMemberId() throws Exception {
        mockMvc.perform(post("/orders/10/cancel")
                        .with(authentication(memberAuthentication()))
                        .with(csrf())
                        .param("reason", "단순 변심"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/orders/10"));

        verify(refundFacade).cancelCustomerOrder(3L, 10L, "단순 변심");
    }

    @Test
    void cancel_reasonOver200_isBadRequest() throws Exception {
        mockMvc.perform(post("/orders/10/cancel")
                        .with(authentication(memberAuthentication()))
                        .with(csrf())
                        .param("reason", "가".repeat(201)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(refundFacade);
    }

    private UsernamePasswordAuthenticationToken memberAuthentication() {
        MemberDetails member = new MemberDetails(new MemberAuthenticationView(
                3L,
                "member@cakeshop.local",
                "dummy",
                "USER",
                true
        ));
        return new UsernamePasswordAuthenticationToken(member, null, member.getAuthorities());
    }
}
