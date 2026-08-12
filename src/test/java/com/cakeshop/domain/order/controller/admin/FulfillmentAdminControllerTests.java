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
import com.cakeshop.domain.order.service.admin.AdminCustomOrderService;
import com.cakeshop.domain.payment.service.RefundFacade;
import com.cakeshop.global.security.MemberDetails;
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
    private AdminCustomOrderService adminCustomOrderService;
    private RefundFacade refundFacade;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        fulfillmentService = Mockito.mock(FulfillmentService.class);
        adminCustomOrderService = Mockito.mock(AdminCustomOrderService.class);
        refundFacade = Mockito.mock(RefundFacade.class);
        mockMvc = MockMvcBuilders.standaloneSetup(
                new FulfillmentAdminController(
                        fulfillmentService,
                        adminCustomOrderService,
                        refundFacade
                )
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
                OrderStatus.READY_FOR_PICKUP,
                List.of()
        );
        when(fulfillmentService.getFulfillments(any())).thenReturn(fulfillment);

        mockMvc.perform(get("/admin/fulfillment")
                        .param("status", "READY_FOR_PICKUP"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/fulfillment/list"))
                .andExpect(model().attribute("fulfillment", fulfillment));

        ArgumentCaptor<FulfillmentSearchCondition> condition =
                ArgumentCaptor.forClass(FulfillmentSearchCondition.class);
        verify(fulfillmentService).getFulfillments(condition.capture());
        org.assertj.core.api.Assertions.assertThat(condition.getValue().getStatus())
                .isEqualTo(OrderStatus.READY_FOR_PICKUP);
    }

    @Test
    void fulfillment_invalidCondition_recoversToDefaultSearch() throws Exception {
        FulfillmentListView fulfillment = new FulfillmentListView(
                null,
                List.of()
        );
        AtomicReference<OrderStatus> capturedStatus = new AtomicReference<>();
        when(fulfillmentService.getFulfillments(any())).thenAnswer(invocation -> {
            FulfillmentSearchCondition condition = invocation.getArgument(0);
            capturedStatus.set(condition.getStatus());
            return fulfillment;
        });

        mockMvc.perform(get("/admin/fulfillment")
                        .param("status", "UNKNOWN"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/fulfillment/list"));

        org.assertj.core.api.Assertions.assertThat(capturedStatus.get()).isNull();
    }

    @Test
    void markPickedUp_admin_redirectsWithSuccessMessage() throws Exception {
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
                        .param("status", "READY_FOR_PICKUP"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/fulfillment?status=READY_FOR_PICKUP"))
                .andExpect(flash().attribute("successMessage", "픽업 완료로 변경했습니다."));

        verify(fulfillmentService).markPickedUp(10L, 7L);
    }

    @Test
    void startProduction_adminRedirectsWithSuccessMessage() throws Exception {
        authenticateAdmin();

        mockMvc.perform(post("/admin/fulfillment/10/production/start")
                        .param("status", "UNDER_REVIEW"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/fulfillment?status=UNDER_REVIEW"))
                .andExpect(flash().attribute("successMessage", "제작을 시작했습니다."));

        verify(adminCustomOrderService).startProduction(10L, 7L);
    }

    @Test
    void reject_adminRedirectsWithSuccessMessage() throws Exception {
        authenticateAdmin();

        mockMvc.perform(post("/admin/fulfillment/10/reject")
                        .param("reason", "제작 일정이 부족합니다.")
                        .param("status", "UNDER_REVIEW"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("successMessage", "주문을 반려하고 결제를 환불했습니다."));

        verify(refundFacade).rejectCustomOrder(7L, 10L, "제작 일정이 부족합니다.");
    }

    private void authenticateAdmin() {
        MemberDetails admin = new MemberDetails(new MemberAuthenticationView(
                7L,
                "admin@cakeshop.local",
                "dummy",
                "ADMIN",
                true
        ));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                admin,
                null,
                admin.getAuthorities()
        ));
    }
}
