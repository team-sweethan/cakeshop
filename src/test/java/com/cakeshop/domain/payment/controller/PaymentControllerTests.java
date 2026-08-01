package com.cakeshop.domain.payment.controller;

import com.cakeshop.domain.member.dto.view.MemberAuthenticationView;
import com.cakeshop.domain.payment.service.PaymentFacade;
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

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class PaymentControllerTests {

    @Mock
    private PaymentFacade paymentFacade;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new PaymentController(paymentFacade)
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
    void confirm_validRequest_usesAuthenticatedMemberAndRedirects() throws Exception {
        mockMvc.perform(post("/orders/1/payment/confirm")
                        .param("paymentKey", "payment-key")
                        .param("tossOrderId", "ORD-100")
                        .param("amount", "30000"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(
                        "/orders/complete?orderId=1"
                ));

        verify(paymentFacade).confirmGeneralPayment(
                eq(10L),
                eq(1L),
                argThat(form ->
                        form.getPaymentKey().equals("payment-key")
                                && form.getTossOrderId().equals("ORD-100")
                                && form.getAmount().compareTo(
                                        BigDecimal.valueOf(30_000)
                                ) == 0
                )
        );
    }
}
