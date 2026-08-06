package com.cakeshop.domain.cart.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.cakeshop.domain.cart.dto.view.CartView;
import com.cakeshop.domain.cart.service.CartService;
import com.cakeshop.domain.member.dto.view.MemberAuthenticationView;
import com.cakeshop.global.security.MemberDetails;
import com.cakeshop.global.security.SecurityConfig;
import java.math.BigDecimal;
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

@WebMvcTest(CartController.class)
@Import(SecurityConfig.class)
class CartControllerSecurityTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CartService cartService;

    @Test
    @WithAnonymousUser
    void cart_anonymousUser_redirectsToLogin() throws Exception {
        mockMvc.perform(get("/cart"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    @WithMockUser(roles = "USER")
    void addItem_withoutCsrf_returnsForbidden() throws Exception {
        mockMvc.perform(post("/cart/items")
                        .param("productId", "10")
                        .param("quantity", "1"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(cartService);
    }

    @Test
    void cart_authenticatedMember_loadsCartByAuthenticatedMemberId() throws Exception {
        MemberDetails memberDetails = new MemberDetails(new MemberAuthenticationView(
                7L,
                "member@cakeshop.local",
                "encoded-password",
                "USER",
                true));
        UsernamePasswordAuthenticationToken token =
                UsernamePasswordAuthenticationToken.authenticated(
                        memberDetails,
                        memberDetails.getPassword(),
                        memberDetails.getAuthorities());
        CartView cart = new CartView(
                List.of(),
                0,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO);
        when(cartService.getCart(7L)).thenReturn(cart);

        mockMvc.perform(get("/cart").with(authentication(token)))
                .andExpect(status().isOk())
                .andExpect(view().name("customer/cart/list"));

        verify(cartService).getCart(7L);
    }
}
