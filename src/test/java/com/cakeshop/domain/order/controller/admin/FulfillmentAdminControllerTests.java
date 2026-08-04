package com.cakeshop.domain.order.controller.admin;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.cakeshop.domain.member.dto.view.MemberAuthenticationView;
import com.cakeshop.domain.order.dto.form.admin.FulfillmentSearchCondition;
import com.cakeshop.domain.order.dto.view.admin.FulfillmentListView;
import com.cakeshop.domain.order.entity.OrderStatus;
import com.cakeshop.domain.order.controller.admin.FulfillmentAdminController;
import com.cakeshop.domain.order.service.admin.FulfillmentService;
import com.cakeshop.global.security.MemberDetails;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class FulfillmentAdminControllerTests {

    private FulfillmentService fulfillmentService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        fulfillmentService = Mockito.mock(FulfillmentService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(
                new FulfillmentAdminController(fulfillmentService)
        ).setCustomArgumentResolvers(
                new AuthenticationPrincipalArgumentResolver()
        ).build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void fulfillment_validCondition_addsActualFulfillmentView() throws Exception {
        FulfillmentListView fulfillment = new FulfillmentListView(
                LocalDate.of(2026, 8, 10),
                OrderStatus.READY_FOR_PICKUP,
                List.of()
        );
        when(fulfillmentService.getFulfillments(any())).thenReturn(fulfillment);

        mockMvc.perform(get("/admin/fulfillment")
                        .param("pickupDate", "2026-08-10")
                        .param("status", "READY_FOR_PICKUP"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/fulfillment/list"))
                .andExpect(model().attribute("fulfillment", fulfillment));

        ArgumentCaptor<FulfillmentSearchCondition> condition =
                ArgumentCaptor.forClass(FulfillmentSearchCondition.class);
        verify(fulfillmentService).getFulfillments(condition.capture());
        org.assertj.core.api.Assertions.assertThat(condition.getValue().getPickupDate())
                .isEqualTo(LocalDate.of(2026, 8, 10));
        org.assertj.core.api.Assertions.assertThat(condition.getValue().getStatus())
                .isEqualTo(OrderStatus.READY_FOR_PICKUP);
    }

    @Test
    void fulfillment_invalidCondition_recoversToDefaultSearch() throws Exception {
        FulfillmentListView fulfillment = new FulfillmentListView(
                LocalDate.of(2026, 8, 2),
                null,
                List.of()
        );
        AtomicReference<LocalDate> capturedDate = new AtomicReference<>();
        AtomicReference<OrderStatus> capturedStatus = new AtomicReference<>();
        when(fulfillmentService.getFulfillments(any())).thenAnswer(invocation -> {
            FulfillmentSearchCondition condition = invocation.getArgument(0);
            capturedDate.set(condition.getPickupDate());
            capturedStatus.set(condition.getStatus());
            return fulfillment;
        });

        mockMvc.perform(get("/admin/fulfillment")
                        .param("pickupDate", "not-a-date")
                        .param("status", "UNKNOWN"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/fulfillment/list"));

        org.assertj.core.api.Assertions.assertThat(capturedDate.get()).isNull();
        org.assertj.core.api.Assertions.assertThat(capturedStatus.get()).isNull();
    }

    @Test
    void markPickedUp_admin_redirectsWithCurrentFilterAndSuccessMessage() throws Exception {
        MemberDetails admin = new MemberDetails(new MemberAuthenticationView(
                7L,
                "admin@cakeshop.local",
                "dummy",
                "ADMIN",
                true
        ));
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                        admin,
                        null,
                        admin.getAuthorities()
                );
        SecurityContextHolder.getContext().setAuthentication(authentication);

        mockMvc.perform(post("/admin/fulfillment/10/pickup")
                        .param("pickupDate", "2026-08-10")
                        .param("status", "READY_FOR_PICKUP"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(
                        "/admin/fulfillment?pickupDate=2026-08-10&status=READY_FOR_PICKUP"
                ))
                .andExpect(flash().attribute("successMessage", "픽업 완료로 변경했습니다."));

        verify(fulfillmentService).markPickedUp(10L, 7L);
    }
}
