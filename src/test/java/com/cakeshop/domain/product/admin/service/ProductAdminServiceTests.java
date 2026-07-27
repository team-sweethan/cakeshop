package com.cakeshop.domain.product.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import com.cakeshop.domain.product.admin.dto.form.ProductAdminSearchCondition;
import com.cakeshop.domain.product.admin.dto.view.ProductAdminListView;
import com.cakeshop.domain.product.entity.ProductStatus;
import com.cakeshop.domain.product.entity.ProductType;
import com.cakeshop.domain.product.error.ProductErrorCode;
import com.cakeshop.domain.product.mapper.ProductMapper;
import com.cakeshop.global.common.paging.PageRequest;
import com.cakeshop.global.common.paging.PageResult;

import com.cakeshop.global.error.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProductAdminServiceTests {

    @Mock
    private ProductMapper productMapper;

    @InjectMocks
    private ProductAdminService productAdminService;

    @Test
    void emptyProductsReturnsEmptyPageWithoutListQuery() {
        ProductAdminSearchCondition condition =
                new ProductAdminSearchCondition();

        condition.setKeyword("   ");

        // 검색 결과가 없는 상황을 만든다.
        when(productMapper.countAdminProducts(any()))
                .thenReturn(0L);

        PageResult<ProductAdminListView> result =
                productAdminService.getProducts(
                        condition,
                        null
                );

        // 빈 검색어가 null로 정리되는지 확인한다.
        assertThat(condition.getKeyword()).isNull();

        // 결과가 없으면 목록 쿼리를 실행하지 않는지 확인한다.
        verify(productMapper, never())
                .findAdminProducts(
                        any(),
                        anyInt(),
                        anyInt()
                );

        assertThat(result.getContent()).isEmpty();
        assertThat(result.getPage()).isEqualTo(1);
        assertThat(result.getSize())
                .isEqualTo(PageRequest.DEFAULT_SIZE);
        assertThat(result.getTotalElements()).isZero();
        assertThat(result.getTotalPages()).isZero();
    }

    @Test
    void productsContainRequestedPageInformation() {
        ProductAdminSearchCondition condition =
                new ProductAdminSearchCondition();

        condition.setKeyword("딸기");

        ProductAdminListView product =
                new ProductAdminListView(
                        1L,
                        "딸기 생크림 케이크",
                        ProductType.GENERAL,
                        BigDecimal.valueOf(35_000),
                        10,
                        ProductStatus.ACTIVE,
                        LocalDateTime.of(
                                2026,
                                7,
                                27,
                                10,
                                0
                        )
                );

        PageRequest pageRequest =
                new PageRequest(2, 10);

        when(productMapper.countAdminProducts(condition))
                .thenReturn(15L);

        when(productMapper.findAdminProducts(
                condition,
                10,
                10
        )).thenReturn(List.of(product));

        PageResult<ProductAdminListView> result =
                productAdminService.getProducts(
                        condition,
                        pageRequest
                );

        verify(productMapper).findAdminProducts(
                condition,
                10,
                10
        );

        assertThat(result.getContent())
                .containsExactly(product);
        assertThat(result.getPage()).isEqualTo(2);
        assertThat(result.getSize()).isEqualTo(10);
        assertThat(result.getTotalElements()).isEqualTo(15);
        assertThat(result.getTotalPages()).isEqualTo(2);
    }

    @Test
    void changeProductStatusUpdatesProduct() {
        when(productMapper.updateProductStatus(
                1L,
                ProductStatus.INACTIVE
        )).thenReturn(1);

        // 판매 중인 상품의 상태를 판매 중지로 변경한다.
        productAdminService.changeProductStatus(
                1L,
                ProductStatus.INACTIVE
        );

        // Mapper에 상품 ID와 변경 상태가 전달됐는지 확인한다.
        verify(productMapper).updateProductStatus(
                1L,
                ProductStatus.INACTIVE
        );
    }

    @Test
    void changeStatusOfMissingProductThrowsException() {
        when(productMapper.updateProductStatus(
                999L,
                ProductStatus.INACTIVE
        )).thenReturn(0);

        // 존재하지 않는 상품은 NOT_FOUND 예외로 처리되는지 확인한다.
        assertThatThrownBy(() ->
                productAdminService.changeProductStatus(
                        999L,
                        ProductStatus.INACTIVE
                )
        )
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(
                                                ProductErrorCode.NOT_FOUND
                                        )
                );
    }
}