package com.cakeshop.domain.product.admin.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
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

import com.cakeshop.domain.product.admin.dto.form.AdminStockFilter;
import com.cakeshop.domain.product.admin.dto.form.ProductAdminSearchCondition;
import com.cakeshop.domain.product.admin.dto.view.ProductAdminListView;
import com.cakeshop.domain.product.admin.service.ProductAdminService;
import com.cakeshop.domain.product.entity.ProductStatus;
import com.cakeshop.domain.product.entity.ProductType;
import com.cakeshop.global.common.paging.PageRequest;
import com.cakeshop.global.common.paging.PageResult;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ProductAdminControllerTests {

    @Test
    void productsBindsSearchConditionAndPageRequest()
            throws Exception {
        ProductAdminService productAdminService =
                mock(ProductAdminService.class);

        PageResult<ProductAdminListView> pageResult =
                new PageResult<>(
                        List.of(),
                        new PageRequest(2, 10),
                        15
                );

        when(productAdminService.getProducts(
                any(),
                any()
        )).thenReturn(pageResult);

        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(
                        new ProductAdminController(
                                productAdminService
                        )
                )
                .build();

        mockMvc.perform(
                        get("/admin/products")
                                .param("keyword", "딸기")
                                .param("type", "GENERAL")
                                .param("status", "ACTIVE")
                                .param("stock", "AVAILABLE")
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
                ))
                .andExpect(model().attributeExists(
                        "condition",
                        "productTypes",
                        "productStatuses",
                        "stockFilters"
                ));

        ArgumentCaptor<ProductAdminSearchCondition>
                conditionCaptor =
                ArgumentCaptor.forClass(
                        ProductAdminSearchCondition.class
                );

        ArgumentCaptor<PageRequest> pageCaptor =
                ArgumentCaptor.forClass(PageRequest.class);

        verify(productAdminService).getProducts(
                conditionCaptor.capture(),
                pageCaptor.capture()
        );

        ProductAdminSearchCondition condition =
                conditionCaptor.getValue();

        assertThat(condition.getKeyword())
                .isEqualTo("딸기");
        assertThat(condition.getType())
                .isEqualTo(ProductType.GENERAL);
        assertThat(condition.getStatus())
                .isEqualTo(ProductStatus.ACTIVE);
        assertThat(condition.getStock())
                .isEqualTo(AdminStockFilter.AVAILABLE);

        PageRequest pageRequest =
                pageCaptor.getValue();

        assertThat(pageRequest.getPage()).isEqualTo(2);
        assertThat(pageRequest.getSize()).isEqualTo(10);
        assertThat(pageRequest.getOffset()).isEqualTo(10);
    }

    @Test
    void changeStatusRedirectsToProductList()
            throws Exception {
        ProductAdminService productAdminService =
                mock(ProductAdminService.class);

        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(
                        new ProductAdminController(
                                productAdminService
                        )
                )
                .build();

        // 판매 중지 요청을 전송한다.
        mockMvc.perform(
                        post("/admin/products/{productId}/status", 1L)
                                .param("status", "INACTIVE")
                )
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(
                        "/admin/products"
                ))
                .andExpect(flash().attribute(
                        "successMessage",
                        "상품 판매를 중지했습니다."
                ));

        // 요청값이 Service로 정확하게 전달됐는지 확인한다.
        verify(productAdminService).changeProductStatus(
                1L,
                ProductStatus.INACTIVE
        );
    }

    @Test
    void startSaleUsesActiveStatus()
            throws Exception {
        ProductAdminService productAdminService =
                mock(ProductAdminService.class);

        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(
                        new ProductAdminController(
                                productAdminService
                        )
                )
                .build();

        // 판매 시작 요청을 전송한다.
        mockMvc.perform(
                        post("/admin/products/{productId}/status", 1L)
                                .param("status", "ACTIVE")
                )
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(
                        "/admin/products"
                ))
                .andExpect(flash().attribute(
                        "successMessage",
                        "상품 판매를 시작했습니다."
                ));

        verify(productAdminService).changeProductStatus(
                1L,
                ProductStatus.ACTIVE
        );
    }
}