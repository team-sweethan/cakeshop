package com.cakeshop.domain.order.controller.admin;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.cakeshop.domain.member.dto.view.MemberAuthenticationView;
import com.cakeshop.domain.order.controller.admin.OrderAdminController;
import com.cakeshop.domain.order.service.admin.AdminOrderService;
import com.cakeshop.domain.order.service.admin.FulfillmentService;
import com.cakeshop.domain.payment.service.RefundFacade;
import com.cakeshop.global.security.MemberDetails;
import com.cakeshop.global.security.SecurityConfig;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(OrderAdminController.class)
@Import(SecurityConfig.class)
class OrderAdminControllerSecurityTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AdminOrderService orderAdminService;

    @MockitoBean
    private FulfillmentService fulfillmentService;

    @MockitoBean
    private RefundFacade refundFacade;

    @Test
    @WithAnonymousUser
    void orders_anonymous_redirectsToLogin() throws Exception {
        mockMvc.perform(get("/admin/orders"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/login"));
    }

    @Test
    @WithMockUser(roles = "USER")
    void orders_customer_isForbidden() throws Exception {
        mockMvc.perform(get("/admin/orders"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void orders_admin_isAccessible() throws Exception {
        when(orderAdminService.getOrders()).thenReturn(List.of());

        mockMvc.perform(get("/admin/orders"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/order/list"));
    }

    @Test
    void legacyOrderPickupRoute_adminCannotProcessPickup() throws Exception {
        MemberDetails admin = adminDetails();
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                admin,
                null,
                admin.getAuthorities()
        );

        mockMvc.perform(post("/admin/orders/10/pickup")
                        .with(authentication(auth))
                        .with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void cancel_missingCsrf_isForbidden() throws Exception {
        mockMvc.perform(post("/admin/orders/10/cancel")
                        .param("reason", "매장 사정"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "USER")
    void cancel_customerIsForbidden() throws Exception {
        mockMvc.perform(post("/admin/orders/10/cancel")
                        .with(csrf())
                        .param("reason", "악의적 요청"))
                .andExpect(status().isForbidden());
    }

    @Test
    void cancel_adminWithCsrf_recordsAuthenticatedAdmin() throws Exception {
        MemberDetails admin = adminDetails();
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                admin,
                null,
                admin.getAuthorities()
        );

        mockMvc.perform(post("/admin/orders/10/cancel")
                        .with(authentication(auth))
                        .with(csrf())
                        .param("reason", "매장 사정"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/orders/10"));

        verify(refundFacade).cancelAdminOrder(7L, 10L, "매장 사정");
    }

    private MemberDetails adminDetails() {
        return new MemberDetails(new MemberAuthenticationView(
                7L,
                "admin@cakeshop.local",
                "dummy",
                "ADMIN",
                true
        ));
    }
}
