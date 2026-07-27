package com.cakeshop.domain.product.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import com.cakeshop.domain.product.admin.dto.view.ProductAdminListView;
import com.cakeshop.domain.product.entity.ProductStatus;
import com.cakeshop.domain.product.entity.ProductType;
import com.cakeshop.domain.product.mapper.ProductMapper;
import com.cakeshop.global.common.paging.PageRequest;
import com.cakeshop.global.common.paging.PageResult;

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
        // 등록된 상품이 없는 상황을 만든다.
        when(productMapper.countAdminProducts())
                .thenReturn(0L);

        // 관리자 상품 목록을 조회한다.
        PageResult<ProductAdminListView> result =
                productAdminService.getProducts(null);

        // 상품이 없으면 목록 조회 쿼리를 실행하지 않는지 확인한다.
        verify(productMapper, never())
                .findAdminProducts(anyInt(), anyInt());

        // 빈 목록과 기본 페이징 정보가 반환되는지 확인한다.
        assertThat(result.getContent()).isEmpty();
        assertThat(result.getPage()).isEqualTo(1);
        assertThat(result.getSize())
                .isEqualTo(PageRequest.DEFAULT_SIZE);
        assertThat(result.getTotalElements()).isZero();
        assertThat(result.getTotalPages()).isZero();
    }

    @Test
    void productsContainRequestedPageInformation() {
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

        // 전체 상품이 15개이고 두 번째 페이지에는 상품 1개가 있다고 설정한다.
        when(productMapper.countAdminProducts())
                .thenReturn(15L);

        when(productMapper.findAdminProducts(10, 10))
                .thenReturn(List.of(product));

        // 두 번째 페이지를 조회한다.
        PageResult<ProductAdminListView> result =
                productAdminService.getProducts(pageRequest);

        // Mapper에 페이지 크기와 offset이 올바르게 전달됐는지 확인한다.
        verify(productMapper)
                .findAdminProducts(10, 10);

        // 조회 결과와 페이지 정보가 올바른지 확인한다.
        assertThat(result.getContent())
                .containsExactly(product);
        assertThat(result.getPage()).isEqualTo(2);
        assertThat(result.getSize()).isEqualTo(10);
        assertThat(result.getTotalElements()).isEqualTo(15);
        assertThat(result.getTotalPages()).isEqualTo(2);
    }
}