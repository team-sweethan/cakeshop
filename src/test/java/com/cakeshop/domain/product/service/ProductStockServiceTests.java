package com.cakeshop.domain.product.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cakeshop.domain.product.entity.Product;
import com.cakeshop.domain.product.entity.ProductStatus;
import com.cakeshop.domain.product.entity.ProductType;
import com.cakeshop.domain.product.error.ProductErrorCode;
import com.cakeshop.domain.product.mapper.ProductMapper;
import com.cakeshop.global.error.BusinessException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProductStockServiceTests {

    @Mock
    private ProductMapper productMapper;

    @InjectMocks
    private ProductStockService productStockService;

    @Test
    void decreaseStock_generalLimitedStock_returnsTrue() {
        when(productMapper.findSalesInfoByIdForUpdate(1L))
                .thenReturn(product(
                        ProductType.GENERAL,
                        ProductStatus.ACTIVE,
                        5
                ));
        when(productMapper.decreaseStockIfAvailable(1L, 3))
                .thenReturn(1);

        boolean stockDeducted =
                productStockService.decreaseStock(1L, 3);

        assertThat(stockDeducted).isTrue();
        verify(productMapper)
                .findSalesInfoByIdForUpdate(1L);
        verify(productMapper)
                .decreaseStockIfAvailable(1L, 3);
        verify(productMapper, never())
                .findSalesInfoById(1L);
    }

    @Test
    void decreaseStock_insufficientStock_throwsInsufficientStock() {
        when(productMapper.findSalesInfoByIdForUpdate(1L))
                .thenReturn(product(
                        ProductType.GENERAL,
                        ProductStatus.ACTIVE,
                        2
                ));
        when(productMapper.decreaseStockIfAvailable(1L, 3))
                .thenReturn(0);

        assertThatThrownBy(() ->
                productStockService.decreaseStock(1L, 3)
        ).isInstanceOfSatisfying(
                BusinessException.class,
                exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(
                                        ProductErrorCode.INSUFFICIENT_STOCK
                                )
        );
    }

    @Test
    void decreaseStock_unlimitedStock_returnsFalse() {
        when(productMapper.findSalesInfoByIdForUpdate(1L))
                .thenReturn(product(
                        ProductType.GENERAL,
                        ProductStatus.ACTIVE,
                        null
                ));

        boolean stockDeducted =
                productStockService.decreaseStock(1L, 3);

        assertThat(stockDeducted).isFalse();
        verify(productMapper, never())
                .decreaseStockIfAvailable(1L, 3);
    }

    @Test
    void decreaseStock_customProduct_returnsFalse() {
        when(productMapper.findSalesInfoByIdForUpdate(1L))
                .thenReturn(product(
                        ProductType.CUSTOM,
                        ProductStatus.ACTIVE,
                        5
                ));

        boolean stockDeducted =
                productStockService.decreaseStock(1L, 3);

        assertThat(stockDeducted).isFalse();
        verify(productMapper, never())
                .decreaseStockIfAvailable(1L, 3);
    }

    @Test
    void decreaseStock_inactiveProduct_throwsNotOnSale() {
        when(productMapper.findSalesInfoByIdForUpdate(1L))
                .thenReturn(product(
                        ProductType.GENERAL,
                        ProductStatus.INACTIVE,
                        5
                ));

        assertThatThrownBy(() ->
                productStockService.decreaseStock(1L, 3)
        ).isInstanceOfSatisfying(
                BusinessException.class,
                exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(
                                        ProductErrorCode.NOT_ON_SALE
                                )
        );
    }

    @Test
    void decreaseStock_missingProduct_throwsNotFound() {
        when(productMapper.findSalesInfoByIdForUpdate(999L))
                .thenReturn(null);

        assertThatThrownBy(() ->
                productStockService.decreaseStock(999L, 1)
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
    void decreaseStock_zeroQuantity_throwsInvalidStockQuantity() {
        assertThatThrownBy(() ->
                productStockService.decreaseStock(1L, 0)
        ).isInstanceOfSatisfying(
                BusinessException.class,
                exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(
                                        ProductErrorCode.INVALID_STOCK_QUANTITY
                                )
        );

        verify(productMapper, never())
                .findSalesInfoByIdForUpdate(1L);
    }

    @Test
    void restoreStock_finiteStock_restoresAfterLockingProduct() {
        when(productMapper.findSalesInfoByIdForUpdate(1L))
                .thenReturn(product(
                        ProductType.GENERAL,
                        ProductStatus.ACTIVE,
                        2
                ));
        when(productMapper.restoreLimitedStock(1L, 3))
                .thenReturn(1);

        productStockService.restoreStock(1L, 3);

        verify(productMapper)
                .findSalesInfoByIdForUpdate(1L);
        verify(productMapper)
                .restoreLimitedStock(1L, 3);
        verify(productMapper, never())
                .findSalesInfoById(1L);
    }

    @Test
    void restoreStock_changedToUnlimited_treatsRestoreAsComplete() {
        when(productMapper.findSalesInfoByIdForUpdate(1L))
                .thenReturn(product(
                        ProductType.CUSTOM,
                        ProductStatus.ACTIVE,
                        null
                ));

        productStockService.restoreStock(1L, 3);

        verify(productMapper, never())
                .restoreLimitedStock(1L, 3);
    }

    @Test
    void restoreStock_finiteStockNotRestored_throwsStockRestoreFailed() {
        when(productMapper.findSalesInfoByIdForUpdate(1L))
                .thenReturn(product(
                        ProductType.GENERAL,
                        ProductStatus.ACTIVE,
                        5
                ));
        when(productMapper.restoreLimitedStock(1L, 3))
                .thenReturn(0);

        assertThatThrownBy(() ->
                productStockService.restoreStock(1L, 3)
        ).isInstanceOfSatisfying(
                BusinessException.class,
                exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(
                                        ProductErrorCode.STOCK_RESTORE_FAILED
                                )
        );
    }

    @Test
    void restoreStock_missingProduct_throwsNotFound() {
        when(productMapper.findSalesInfoByIdForUpdate(999L))
                .thenReturn(null);

        assertThatThrownBy(() ->
                productStockService.restoreStock(999L, 1)
        ).isInstanceOfSatisfying(
                BusinessException.class,
                exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(
                                        ProductErrorCode.NOT_FOUND
                                )
        );

        verify(productMapper, never())
                .restoreLimitedStock(999L, 1);
    }

    @Test
    void restoreStock_zeroQuantity_throwsInvalidStockQuantity() {
        assertThatThrownBy(() ->
                productStockService.restoreStock(1L, 0)
        ).isInstanceOfSatisfying(
                BusinessException.class,
                exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(
                                        ProductErrorCode.INVALID_STOCK_QUANTITY
                                )
        );

        verify(productMapper, never())
                .findSalesInfoByIdForUpdate(1L);
        verify(productMapper, never())
                .restoreLimitedStock(1L, 0);
    }

    private Product product(
            ProductType productType,
            ProductStatus status,
            Integer stockQuantity
    ) {
        Product product = new Product();

        product.setId(1L);
        product.setProductType(productType);
        product.setStatus(status);
        product.setStockQuantity(stockQuantity);

        return product;
    }
}
