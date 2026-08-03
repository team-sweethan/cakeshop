package com.cakeshop.domain.product.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cakeshop.domain.product.dto.form.ProductImageUploadForm;
import com.cakeshop.domain.product.error.ProductErrorCode;
import com.cakeshop.domain.product.service.ProductImageService;
import com.cakeshop.global.error.BusinessException;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ProductImageAdminControllerTests {

    @Test
    void uploadImage_validFile_redirectsWithSuccessMessage()
            throws Exception {
        ProductImageService service =
                mock(ProductImageService.class);
        MockMvc mockMvc = mockMvc(service);
        MockMultipartFile imageFile = imageFile();

        mockMvc.perform(
                        multipart("/admin/products/1/images")
                                .file(imageFile)
                )
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(
                        "/admin/products/1/edit"
                ))
                .andExpect(flash().attribute(
                        "successMessage",
                        "상품 이미지를 추가했습니다."
                ));

        ArgumentCaptor<ProductImageUploadForm> formCaptor =
                ArgumentCaptor.forClass(
                        ProductImageUploadForm.class
                );
        verify(service).uploadImage(
                eq(1L),
                formCaptor.capture()
        );
        assertThat(formCaptor.getValue().getImageFile())
                .isSameAs(imageFile);
    }

    @Test
    void uploadImage_fileMissing_redirectsWithValidationMessage()
            throws Exception {
        ProductImageService service =
                mock(ProductImageService.class);
        MockMvc mockMvc = mockMvc(service);

        mockMvc.perform(
                        multipart("/admin/products/1/images")
                )
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(
                        "/admin/products/1/edit"
                ))
                .andExpect(flash().attribute(
                        "errorMessage",
                        "상품 이미지를 선택해 주세요."
                ));

        verify(service, never()).uploadImage(
                anyLong(),
                any(ProductImageUploadForm.class)
        );
    }

    @Test
    void uploadImage_businessError_redirectsWithPublicErrorMessage()
            throws Exception {
        ProductImageService service =
                mock(ProductImageService.class);
        doThrow(new BusinessException(
                ProductErrorCode.INVALID_IMAGE_FILE
        )).when(service).uploadImage(
                anyLong(),
                any(ProductImageUploadForm.class)
        );
        MockMvc mockMvc = mockMvc(service);

        mockMvc.perform(
                        multipart("/admin/products/1/images")
                                .file(imageFile())
                )
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(
                        "/admin/products/1/edit"
                ))
                .andExpect(flash().attribute(
                        "errorMessage",
                        ProductErrorCode.INVALID_IMAGE_FILE
                                .message()
                ));
    }

    @Test
    void deleteImage_existingImage_redirectsWithSuccessMessage()
            throws Exception {
        ProductImageService service =
                mock(ProductImageService.class);
        MockMvc mockMvc = mockMvc(service);

        mockMvc.perform(post(
                        "/admin/products/1/images/10/delete"
                ))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(
                        "/admin/products/1/edit"
                ))
                .andExpect(flash().attribute(
                        "successMessage",
                        "상품 이미지를 삭제했습니다."
                ));

        verify(service).deleteImage(1L, 10L);
    }

    @Test
    void deleteImage_missingProduct_redirectsWithPublicErrorMessage()
            throws Exception {
        ProductImageService service =
                mock(ProductImageService.class);
        doThrow(new BusinessException(
                ProductErrorCode.NOT_FOUND
        )).when(service).deleteImage(1L, 10L);
        MockMvc mockMvc = mockMvc(service);

        mockMvc.perform(post(
                        "/admin/products/1/images/10/delete"
                ))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(
                        "/admin/products/1/edit"
                ))
                .andExpect(flash().attribute(
                        "errorMessage",
                        ProductErrorCode.NOT_FOUND.message()
                ));
    }

    @Test
    void deleteImage_missingImage_redirectsWithPublicErrorMessage()
            throws Exception {
        ProductImageService service =
                mock(ProductImageService.class);
        doThrow(new BusinessException(
                ProductErrorCode.IMAGE_NOT_FOUND
        )).when(service).deleteImage(1L, 10L);
        MockMvc mockMvc = mockMvc(service);

        mockMvc.perform(post(
                        "/admin/products/1/images/10/delete"
                ))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(
                        "/admin/products/1/edit"
                ))
                .andExpect(flash().attribute(
                        "errorMessage",
                        ProductErrorCode.IMAGE_NOT_FOUND.message()
                ));
    }

    private MockMvc mockMvc(ProductImageService service) {
        return MockMvcBuilders.standaloneSetup(
                new ProductImageAdminController(service)
        ).build();
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
