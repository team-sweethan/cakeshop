package com.cakeshop.domain.payment.controller;

import com.cakeshop.domain.payment.dto.view.PaymentAdminListView;
import com.cakeshop.domain.payment.dto.view.PaymentAdminSummaryView;
import com.cakeshop.domain.payment.service.PaymentAdminService;
import com.cakeshop.global.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PaymentAdminController.class)
@Import(SecurityConfig.class)
class PaymentAdminControllerSecurityTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PaymentAdminService paymentAdminQueryService;

    @Test
    @WithMockUser(roles = "USER")
    void payments_customer_isForbidden() throws Exception {
        mockMvc.perform(get("/admin/payments"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void payments_admin_isAllowed() throws Exception {
        when(paymentAdminQueryService.getPayments(any()))
                .thenReturn(new PaymentAdminListView(
                        null,
                        new PaymentAdminSummaryView(0, 0, 0, 0),
                        List.of()
                ));

        mockMvc.perform(get("/admin/payments"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "USER")
    void updateExpirationCheck_customer_isForbidden() throws Exception {
        mockMvc.perform(post("/admin/payments/7/expiration-check").with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void updateExpirationCheck_adminWithCsrf_updatesState() throws Exception {
        mockMvc.perform(post("/admin/payments/7/expiration-check")
                        .with(csrf())
                        .param("checked", "true"))
                .andExpect(status().is3xxRedirection());

        verify(paymentAdminQueryService).updateExpirationCheck(7L, true);
    }
}
