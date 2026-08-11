package com.cakeshop.domain.product.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cakeshop.domain.product.dto.form.ProductImageUploadForm;
import com.cakeshop.domain.product.service.ProductImageService;
import com.cakeshop.global.security.SecurityConfig;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ProductImageAdminController.class)
@Import(SecurityConfig.class)
class ProductImageAdminControllerSecurityTests {

    private static final String DELETE_URL =
            "/admin/products/1/images/10/delete";

    private static final String REPLACE_URL =
            "/admin/products/1/images/10/replace";

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
                .andExpect(redirectedUrl("/admin/login"));

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

    @Test
    @WithAnonymousUser
    void replaceImage_anonymousUser_redirectsToLogin()
            throws Exception {
        mockMvc.perform(
                        multipart(REPLACE_URL)
                                .file(imageFile())
                                .with(csrf())
                )
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/login"));

        verify(productImageService, never()).replaceImage(
                eq(1L),
                eq(10L),
                any(ProductImageUploadForm.class)
        );
    }

    @Test
    @WithMockUser(roles = "USER")
    void replaceImage_customerRole_returnsForbidden()
            throws Exception {
        mockMvc.perform(
                        multipart(REPLACE_URL)
                                .file(imageFile())
                                .with(csrf())
                )
                .andExpect(status().isForbidden());

        verify(productImageService, never()).replaceImage(
                eq(1L),
                eq(10L),
                any(ProductImageUploadForm.class)
        );
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void replaceImage_missingCsrfToken_returnsForbidden()
            throws Exception {
        mockMvc.perform(
                        multipart(REPLACE_URL)
                                .file(imageFile())
                )
                .andExpect(status().isForbidden());

        verify(productImageService, never()).replaceImage(
                eq(1L),
                eq(10L),
                any(ProductImageUploadForm.class)
        );
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void replaceImage_adminWithCsrf_redirectsToProductEdit()
            throws Exception {
        mockMvc.perform(
                        multipart(REPLACE_URL)
                                .file(imageFile())
                                .with(csrf())
                )
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(
                        "/admin/products/1/edit"
                ));

        verify(productImageService).replaceImage(
                eq(1L),
                eq(10L),
                any(ProductImageUploadForm.class)
        );
    }

    private MockMultipartFile imageFile() {
        return new MockMultipartFile(
                "imageFile",
                "cake.jpg",
                "image/jpeg",
                new byte[] {
                        (byte) 0xFF,
                        (byte) 0xD8,
                        (byte) 0xFF
                }
        );
    }
}
