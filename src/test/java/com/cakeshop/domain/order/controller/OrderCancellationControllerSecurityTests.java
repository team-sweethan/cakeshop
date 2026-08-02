package com.cakeshop.domain.order.controller;

import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cakeshop.domain.member.dto.view.MemberAuthenticationView;
import com.cakeshop.domain.payment.service.RefundFacade;
import com.cakeshop.global.security.MemberDetails;
import com.cakeshop.global.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(OrderCancellationController.class)
@Import(SecurityConfig.class)
class OrderCancellationControllerSecurityTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RefundFacade refundFacade;

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
