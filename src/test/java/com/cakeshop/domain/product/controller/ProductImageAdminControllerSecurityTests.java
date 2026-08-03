package com.cakeshop.domain.product.controller;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cakeshop.domain.product.service.ProductImageService;
import com.cakeshop.global.security.SecurityConfig;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ProductImageAdminController.class)
@Import(SecurityConfig.class)
class ProductImageAdminControllerSecurityTests {

    private static final String DELETE_URL =
            "/admin/products/1/images/10/delete";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProductImageService productImageService;

    @Test
    @WithAnonymousUser
    void deleteImage_anonymousUser_redirectsToLogin()
            throws Exception {
        mockMvc.perform(post(DELETE_URL).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));

        verify(productImageService, never())
                .deleteImage(1L, 10L);
    }

    @Test
    @WithMockUser(roles = "USER")
    void deleteImage_customerRole_returnsForbidden()
            throws Exception {
        mockMvc.perform(post(DELETE_URL).with(csrf()))
                .andExpect(status().isForbidden());

        verify(productImageService, never())
                .deleteImage(1L, 10L);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void deleteImage_missingCsrfToken_returnsForbidden()
            throws Exception {
        mockMvc.perform(post(DELETE_URL))
                .andExpect(status().isForbidden());

        verify(productImageService, never())
                .deleteImage(1L, 10L);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void deleteImage_adminWithCsrf_redirectsToProductEdit()
            throws Exception {
        mockMvc.perform(post(DELETE_URL).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(
                        "/admin/products/1/edit"
                ));

        verify(productImageService).deleteImage(1L, 10L);
    }
}
