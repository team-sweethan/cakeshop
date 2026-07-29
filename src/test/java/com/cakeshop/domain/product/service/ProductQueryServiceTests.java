package com.cakeshop.domain.product.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;

import com.cakeshop.domain.product.dto.view.ProductSalesInfo;
import com.cakeshop.domain.product.entity.Product;
import com.cakeshop.domain.product.entity.ProductStatus;
import com.cakeshop.domain.product.error.ProductErrorCode;
import com.cakeshop.domain.product.mapper.ProductMapper;
import com.cakeshop.global.error.BusinessException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProductQueryServiceTests {

    @Mock
    private ProductMapper productMapper;

    @InjectMocks
    private ProductQueryService productQueryService;

    @Test
    void getSalesInfo_activeProduct_returnsSalesInfo() {
        Product product = product(
                ProductStatus.ACTIVE,
                5
        );

        when(productMapper.findSalesInfoById(1L))
                .thenReturn(product);

        ProductSalesInfo result =
                productQueryService.getSalesInfo(1L);

        assertThat(result.productId()).isEqualTo(1L);
        assertThat(result.available()).isTrue();
        assertThat(result.basePrice())
                .isEqualByComparingTo("35000");
        assertThat(result.stockQuantity()).isEqualTo(5);
    }

    @Test
    void getSalesInfo_outOfStockProduct_returnsUnavailableSalesInfo() {
        Product product = product(
                ProductStatus.ACTIVE,
                0
        );

        when(productMapper.findSalesInfoById(1L))
                .thenReturn(product);

        ProductSalesInfo result =
                productQueryService.getSalesInfo(1L);

        assertThat(result.available()).isFalse();
        assertThat(result.stockQuantity()).isZero();
    }

    @Test
    void getSalesInfo_unlimitedStockProduct_returnsAvailableSalesInfo() {
        Product product = product(
                ProductStatus.ACTIVE,
                null
        );

        when(productMapper.findSalesInfoById(1L))
                .thenReturn(product);

        ProductSalesInfo result =
                productQueryService.getSalesInfo(1L);

        assertThat(result.available()).isTrue();
        assertThat(result.stockQuantity()).isNull();
    }

    @Test
    void getSalesInfo_missingProduct_throwsNotFound() {
        when(productMapper.findSalesInfoById(999L))
                .thenReturn(null);

        assertThatThrownBy(() ->
                productQueryService.getSalesInfo(999L)
        ).isInstanceOfSatisfying(
                BusinessException.class,
                exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(
                                        ProductErrorCode.NOT_FOUND
                                )
        );
    }

    @Test
    void getSalesInfo_inactiveProduct_throwsNotOnSale() {
        Product product = product(
                ProductStatus.INACTIVE,
                5
        );

        when(productMapper.findSalesInfoById(1L))
                .thenReturn(product);

        assertThatThrownBy(() ->
                productQueryService.getSalesInfo(1L)
        ).isInstanceOfSatisfying(
                BusinessException.class,
                exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(
                                        ProductErrorCode.NOT_ON_SALE
                                )
        );
    }

    private Product product(
            ProductStatus status,
            Integer stockQuantity
    ) {
        Product product = new Product();

        product.setId(1L);
        product.setBasePrice(
                BigDecimal.valueOf(35_000)
        );
        product.setStockQuantity(stockQuantity);
        product.setStatus(status);

        return product;
    }
}
