package com.cakeshop.domain.payment.controller;

import com.cakeshop.domain.payment.dto.form.PaymentAdminSearchCondition;
import com.cakeshop.domain.payment.dto.view.PaymentAdminListView;
import com.cakeshop.domain.payment.dto.view.PaymentAdminSummaryView;
import com.cakeshop.domain.payment.entity.PaymentStatus;
import com.cakeshop.domain.payment.service.PaymentAdminService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

class PaymentAdminControllerTests {

    private PaymentAdminService paymentAdminQueryService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        paymentAdminQueryService = Mockito.mock(PaymentAdminService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(
                new PaymentAdminController(paymentAdminQueryService)
        ).build();
    }

    @Test
    void payments_validStatus_addsActualPaymentList() throws Exception {
        PaymentAdminListView paymentList = new PaymentAdminListView(
                PaymentStatus.DONE,
                new PaymentAdminSummaryView(3, 2, 1, 0),
                List.of()
        );
        when(paymentAdminQueryService.getPayments(any())).thenReturn(paymentList);

        mockMvc.perform(get("/admin/payments").param("status", "DONE"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/payment/list"))
                .andExpect(model().attribute("paymentList", paymentList));

        ArgumentCaptor<PaymentAdminSearchCondition> condition =
                ArgumentCaptor.forClass(PaymentAdminSearchCondition.class);
        verify(paymentAdminQueryService).getPayments(condition.capture());
        assertThat(condition.getValue().getStatus()).isEqualTo(PaymentStatus.DONE);
    }

    @Test
    void payments_invalidStatus_recoversToAllPayments() throws Exception {
        when(paymentAdminQueryService.getPayments(any())).thenAnswer(invocation -> {
            PaymentAdminSearchCondition condition = invocation.getArgument(0);
            return new PaymentAdminListView(
                    condition.getStatus(),
                    new PaymentAdminSummaryView(0, 0, 0, 0),
                    List.of()
            );
        });

        mockMvc.perform(get("/admin/payments").param("status", "UNKNOWN"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/payment/list"));

        ArgumentCaptor<PaymentAdminSearchCondition> condition =
                ArgumentCaptor.forClass(PaymentAdminSearchCondition.class);
        verify(paymentAdminQueryService).getPayments(condition.capture());
        assertThat(condition.getValue().getStatus()).isNull();
    }
}
