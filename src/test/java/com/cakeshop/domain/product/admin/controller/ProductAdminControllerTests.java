package com.cakeshop.domain.product.admin.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.util.List;

import com.cakeshop.domain.product.admin.dto.view.ProductAdminListView;
import com.cakeshop.domain.product.admin.service.ProductAdminService;
import com.cakeshop.global.common.paging.PageRequest;
import com.cakeshop.global.common.paging.PageResult;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ProductAdminControllerTests {

    @Test
    void productsReturnsAdminProductListPage() throws Exception {
        ProductAdminService productAdminService =
                mock(ProductAdminService.class);

        PageResult<ProductAdminListView> pageResult =
                new PageResult<>(
                        List.of(),
                        new PageRequest(2, 10),
                        15
                );

        when(productAdminService.getProducts(any()))
                .thenReturn(pageResult);

        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(
                        new ProductAdminController(
                                productAdminService
                        )
                )
                .build();

        // 관리자 상품 목록의 두 번째 페이지를 요청한다.
        mockMvc.perform(
                        get("/admin/products")
                                .param("page", "2")
                                .param("size", "10")
                )
                .andExpect(status().isOk())
                .andExpect(view().name(
                        "admin/product/list"
                ))
                .andExpect(model().attribute(
                        "pageResult",
                        pageResult
                ));

        ArgumentCaptor<PageRequest> pageCaptor =
                ArgumentCaptor.forClass(PageRequest.class);

        verify(productAdminService)
                .getProducts(pageCaptor.capture());

        // 요청값이 PageRequest로 올바르게 변환됐는지 확인한다.
        PageRequest capturedPageRequest =
                pageCaptor.getValue();

        assertThat(capturedPageRequest.getPage())
                .isEqualTo(2);
        assertThat(capturedPageRequest.getSize())
                .isEqualTo(10);
        assertThat(capturedPageRequest.getOffset())
                .isEqualTo(10);
    }
}