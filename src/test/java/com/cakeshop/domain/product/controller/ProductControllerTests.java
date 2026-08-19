package com.cakeshop.domain.product.controller;

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

import com.cakeshop.domain.product.dto.form.ProductSearchCondition;
import com.cakeshop.domain.product.dto.form.ProductSort;
import com.cakeshop.domain.product.dto.form.StockFilter;
import com.cakeshop.domain.product.dto.view.ProductListView;
import com.cakeshop.domain.product.entity.ProductType;
import com.cakeshop.domain.product.service.ProductService;
import com.cakeshop.domain.review.service.ReviewProductQueryService;
import com.cakeshop.global.common.paging.PageRequest;
import com.cakeshop.global.common.paging.PageResult;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ProductControllerTests {

    @Test
    void listBindsSearchConditionAndUsesEightItemsPerPage() throws Exception {
        ProductService productService =
                mock(ProductService.class);

        PageResult<ProductListView> pageResult =
                new PageResult<>(
                        List.of(),
                        new PageRequest(2, 8),
                        8
                );

        when(productService.getPublicProducts(any(), any()))
                .thenReturn(pageResult);

        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(
                        new ProductController(
                                productService,
                                mock(ReviewProductQueryService.class))
                )
                .build();

        mockMvc.perform(get("/products")
                        .param("keyword", "딸기")
                        .param("type", "GENERAL")
                        .param("stock", "AVAILABLE")
                        .param("sort", "PRICE_ASC")
                        .param("page", "2"))
                .andExpect(status().isOk())
                .andExpect(view().name("customer/product/list"))
                .andExpect(model().attribute(
                        "pageResult",
                        pageResult
                ))
                .andExpect(model().attribute(
                        "productListTitle",
                        "상품 목록 - 일반 케이크"
                ))
                .andExpect(model().attributeExists(
                        "condition",
                        "productTypes",
                        "stockFilters",
                        "sortOptions"
                ));

        ArgumentCaptor<ProductSearchCondition> conditionCaptor =
                ArgumentCaptor.forClass(
                        ProductSearchCondition.class
                );
        ArgumentCaptor<PageRequest> pageCaptor =
                ArgumentCaptor.forClass(PageRequest.class);

        verify(productService).getPublicProducts(
                conditionCaptor.capture(),
                pageCaptor.capture()
        );

        ProductSearchCondition condition =
                conditionCaptor.getValue();

        assertThat(condition.getKeyword()).isEqualTo("딸기");
        assertThat(condition.getType())
                .isEqualTo(ProductType.GENERAL);
        assertThat(condition.getStock())
                .isEqualTo(StockFilter.AVAILABLE);
        assertThat(condition.getSort())
                .isEqualTo(ProductSort.PRICE_ASC);

        assertThat(pageCaptor.getValue().getPage()).isEqualTo(2);
        assertThat(pageCaptor.getValue().getSize()).isEqualTo(8);
        assertThat(pageCaptor.getValue().getOffset()).isEqualTo(8);
    }

    @Test
    void invalidSearchValuesFallBackToSafeDefaults() throws Exception {
        ProductService productService =
                mock(ProductService.class);

        PageResult<ProductListView> pageResult =
                new PageResult<>(
                        List.of(),
                        new PageRequest(1, 8),
                        0
                );

        when(productService.getPublicProducts(any(), any()))
                .thenReturn(pageResult);

        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(
                        new ProductController(
                                productService,
                                mock(ReviewProductQueryService.class))
                )
                .build();

        mockMvc.perform(get("/products")
                        .param("type", "UNKNOWN")
                        .param("stock", "UNKNOWN")
                        .param("sort", "UNKNOWN")
                        .param("minPrice", "not-a-number")
                        .param("maxPrice", "not-a-number")
                        .param("page", "not-a-number")
                        .param("size", "-1"))
                .andExpect(status().isOk())
                .andExpect(view().name("customer/product/list"))
                .andExpect(model().attribute(
                        "productListTitle",
                        "상품 목록"
                ));

        ArgumentCaptor<ProductSearchCondition> conditionCaptor =
                ArgumentCaptor.forClass(
                        ProductSearchCondition.class
                );
        ArgumentCaptor<PageRequest> pageCaptor =
                ArgumentCaptor.forClass(PageRequest.class);

        verify(productService).getPublicProducts(
                conditionCaptor.capture(),
                pageCaptor.capture()
        );

        ProductSearchCondition condition =
                conditionCaptor.getValue();

        assertThat(condition.getType()).isNull();
        assertThat(condition.getStock()).isNull();
        assertThat(condition.getSort())
                .isEqualTo(ProductSort.POPULAR);
        assertThat(condition.getMinPrice())
                .isEqualByComparingTo("0");
        assertThat(condition.getMaxPrice()).isNull();

        assertThat(pageCaptor.getValue().getPage()).isEqualTo(1);
        assertThat(pageCaptor.getValue().getSize()).isEqualTo(8);
    }
}
