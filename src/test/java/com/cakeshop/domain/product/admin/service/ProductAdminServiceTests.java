package com.cakeshop.domain.product.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import com.cakeshop.domain.product.admin.dto.form.ProductAdminSearchCondition;
import com.cakeshop.domain.product.admin.dto.form.ProductForm;
import com.cakeshop.domain.product.admin.dto.view.ProductAdminListView;
import com.cakeshop.domain.product.admin.dto.view.ProductCategoryOptionView;
import com.cakeshop.domain.product.admin.dto.view.ProductOptionAdminRow;
import com.cakeshop.domain.product.entity.Product;
import com.cakeshop.domain.product.entity.ProductOptionSelectionType;
import com.cakeshop.domain.product.entity.ProductOptionStatus;
import com.cakeshop.domain.product.entity.ProductStatus;
import com.cakeshop.domain.product.entity.ProductType;
import com.cakeshop.domain.product.error.ProductErrorCode;
import com.cakeshop.domain.product.mapper.ProductMapper;
import com.cakeshop.global.common.paging.PageRequest;
import com.cakeshop.global.common.paging.PageResult;

import com.cakeshop.global.error.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
        when(productMapper.findSalesInfoByIdForUpdate(1L))
                .thenReturn(productWithStatus(ProductStatus.ACTIVE));
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
        when(productMapper.findSalesInfoByIdForUpdate(999L))
                .thenReturn(null);

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

        verify(productMapper, never())
                .updateProductStatus(
                        anyLong(),
                        any(ProductStatus.class)
                );
    }

    @Test
    void startSale_requiredGroupWithoutActiveOption_throwsPolicyError() {
        when(productMapper.findSalesInfoByIdForUpdate(1L))
                .thenReturn(productWithStatus(ProductStatus.INACTIVE));
        when(productMapper
                .findAdminOptionRowsByProductIdForUpdate(1L))
                .thenReturn(List.of(
                        requiredGroupRow(
                                null,
                                null
                        )
                ));

        assertThatThrownBy(() ->
                productAdminService.changeProductStatus(
                        1L,
                        ProductStatus.ACTIVE
                )
        ).isInstanceOfSatisfying(
                BusinessException.class,
                exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(
                                        ProductErrorCode
                                                .REQUIRED_OPTION_GROUP_EMPTY
                                )
        );

        verify(productMapper, never())
                .updateProductStatus(
                        anyLong(),
                        any(ProductStatus.class)
                );
    }

    @Test
    void startSale_requiredGroupWithActiveOption_updatesProduct() {
        when(productMapper.findSalesInfoByIdForUpdate(1L))
                .thenReturn(productWithStatus(ProductStatus.INACTIVE));
        when(productMapper
                .findAdminOptionRowsByProductIdForUpdate(1L))
                .thenReturn(List.of(
                        requiredGroupRow(
                                11L,
                                ProductOptionStatus.ACTIVE
                        )
                ));
        when(productMapper.updateProductStatus(
                1L,
                ProductStatus.ACTIVE
        )).thenReturn(1);

        productAdminService.changeProductStatus(
                1L,
                ProductStatus.ACTIVE
        );

        verify(productMapper).updateProductStatus(
                1L,
                ProductStatus.ACTIVE
        );
    }

    @Test
    void activeCategoriesAreReturned() {
        ProductCategoryOptionView category =
                new ProductCategoryOptionView(
                        1L,
                        "케이크"
                );

        when(productMapper.findActiveCategories())
                .thenReturn(List.of(category));

        List<ProductCategoryOptionView> result =
                productAdminService.getActiveCategories();

        assertThat(result).containsExactly(category);
    }

    @Test
    void createProductRegistersInactiveProduct() {
        ProductForm form = validProductForm();

        when(productMapper.existsActiveCategoryById(1L))
                .thenReturn(true);

        // Mapper가 상품을 등록하면서 생성된 ID를 설정하는 상황을 만든다.
        when(productMapper.insertProduct(any(Product.class)))
                .thenAnswer(invocation -> {
                    Product product =
                            invocation.getArgument(0);

                    product.setId(10L);

                    return 1;
                });

        long productId =
                productAdminService.createProduct(form);

        ArgumentCaptor<Product> productCaptor =
                ArgumentCaptor.forClass(Product.class);

        verify(productMapper)
                .insertProduct(productCaptor.capture());

        Product savedProduct =
                productCaptor.getValue();

        assertThat(productId).isEqualTo(10L);
        assertThat(savedProduct.getCategoryId())
                .isEqualTo(1L);
        assertThat(savedProduct.getName())
                .isEqualTo("신규 케이크");
        assertThat(savedProduct.getDescription())
                .isNull();
        assertThat(savedProduct.getBasePrice())
                .isEqualByComparingTo("35000");
        assertThat(savedProduct.getStockQuantity())
                .isEqualTo(10);
        assertThat(savedProduct.getProductType())
                .isEqualTo(ProductType.GENERAL);
        assertThat(savedProduct.getPreparationDays())
                .isZero();
        assertThat(savedProduct.getStatus())
                .isEqualTo(ProductStatus.INACTIVE);
    }

    @Test
    void createProduct_generalWithSubmittedPreparation_normalizesPreparationToZero() {
        ProductForm form = validProductForm();

        form.setPreparationDays(3);

        when(productMapper.existsActiveCategoryById(1L))
                .thenReturn(true);
        when(productMapper.insertProduct(any(Product.class)))
                .thenAnswer(invocation -> {
                    Product product =
                            invocation.getArgument(0);

                    product.setId(10L);

                    return 1;
                });

        productAdminService.createProduct(form);

        ArgumentCaptor<Product> productCaptor =
                ArgumentCaptor.forClass(Product.class);

        verify(productMapper)
                .insertProduct(productCaptor.capture());

        Product savedProduct =
                productCaptor.getValue();

        assertThat(savedProduct.getPreparationDays()).isZero();
    }

    @Test
    void createProduct_customPreparationLessThanOne_throwsException() {
        ProductForm form = validProductForm();

        form.setProductType(ProductType.CUSTOM);
        form.setPreparationDays(0);

        when(productMapper.existsActiveCategoryById(1L))
                .thenReturn(true);

        assertThatThrownBy(() ->
                productAdminService.createProduct(form)
        )
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(
                                                ProductErrorCode.INVALID_PRODUCT_POLICY
                                        )
                );

        verify(productMapper, never())
                .insertProduct(any(Product.class));
    }

    @Test
    void createProductWithInactiveCategoryThrowsException() {
        ProductForm form = validProductForm();

        when(productMapper.existsActiveCategoryById(1L))
                .thenReturn(false);

        assertThatThrownBy(() ->
                productAdminService.createProduct(form)
        )
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(
                                                ProductErrorCode.INVALID_CATEGORY
                                        )
                );

        verify(productMapper, never())
                .insertProduct(any(Product.class));
    }

    @Test
    void existingProductFormIsReturned() {
        ProductForm form = validProductForm();

        when(productMapper.findAdminProductFormById(1L))
                .thenReturn(form);

        ProductForm result =
                productAdminService.getProductForm(1L);

        assertThat(result).isSameAs(form);
        verify(productMapper)
                .findAdminProductFormById(1L);
    }

    @Test
    void missingProductFormThrowsNotFoundException() {
        when(productMapper.findAdminProductFormById(999L))
                .thenReturn(null);

        assertThatThrownBy(() ->
                productAdminService.getProductForm(999L)
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

    @Test
    void updateProductChangesOnlyBasicInformation() {
        ProductForm existingForm = validProductForm();
        ProductForm updateForm = validProductForm();

        updateForm.setName("  수정 케이크  ");
        updateForm.setDescription("  수정된 상품 설명  ");
        updateForm.setBasePrice(
                BigDecimal.valueOf(45_000)
        );
        updateForm.setStockQuantity(null);
        updateForm.setProductType(ProductType.CUSTOM);
        updateForm.setPreparationDays(3);

        when(productMapper.findAdminProductFormById(1L))
                .thenReturn(existingForm);
        when(productMapper.existsActiveCategoryById(1L))
                .thenReturn(true);
        when(productMapper.updateProduct(
                any(Product.class),
                any()
        ))
                .thenReturn(1);

        productAdminService.updateProduct(
                1L,
                updateForm
        );

        ArgumentCaptor<Product> productCaptor =
                ArgumentCaptor.forClass(Product.class);

        verify(productMapper)
                .updateProduct(
                        productCaptor.capture(),
                        any()
                );

        Product updatedProduct =
                productCaptor.getValue();

        assertThat(updatedProduct.getId()).isEqualTo(1L);
        assertThat(updatedProduct.getCategoryId())
                .isEqualTo(1L);
        assertThat(updatedProduct.getName())
                .isEqualTo("수정 케이크");
        assertThat(updatedProduct.getDescription())
                .isEqualTo("수정된 상품 설명");
        assertThat(updatedProduct.getBasePrice())
                .isEqualByComparingTo("45000");
        assertThat(updatedProduct.getStockQuantity())
                .isNull();
        assertThat(updateForm.getOriginalStockQuantity())
                .isEqualTo(10);
        assertThat(updatedProduct.getProductType())
                .isEqualTo(ProductType.CUSTOM);
        assertThat(updatedProduct.getPreparationDays())
                .isEqualTo(3);

        // 기본 정보 수정에서는 판매 상태를 변경하지 않는다.
        assertThat(updatedProduct.getStatus()).isNull();
    }

    @Test
    void updateMissingProductThrowsNotFoundException() {
        ProductForm form = validProductForm();

        when(productMapper.findAdminProductFormById(999L))
                .thenReturn(null);

        assertThatThrownBy(() ->
                productAdminService.updateProduct(
                        999L,
                        form
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

        verify(productMapper, never())
                .existsActiveCategoryById(anyLong());
        verify(productMapper, never())
                .updateProduct(
                        any(Product.class),
                        any()
                );
    }

    @Test
    void updateProductWithInactiveCategoryThrowsException() {
        ProductForm form = validProductForm();

        when(productMapper.findAdminProductFormById(1L))
                .thenReturn(validProductForm());
        when(productMapper.existsActiveCategoryById(1L))
                .thenReturn(false);

        assertThatThrownBy(() ->
                productAdminService.updateProduct(
                        1L,
                        form
                )
        )
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(
                                                ProductErrorCode.INVALID_CATEGORY
                                        )
                );

        verify(productMapper, never())
                .updateProduct(
                        any(Product.class),
                        any()
                );
    }

    @Test
    void updateProductWithChangedStockThrowsUpdateConflictException() {
        ProductForm form = validProductForm();

        when(productMapper.findAdminProductFormById(1L))
                .thenReturn(
                        validProductForm(),
                        validProductForm()
                );
        when(productMapper.existsActiveCategoryById(1L))
                .thenReturn(true);
        when(productMapper.updateProduct(
                any(Product.class),
                any()
        ))
                .thenReturn(0);

        assertThatThrownBy(() ->
                productAdminService.updateProduct(
                        1L,
                        form
                )
        )
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(
                                                ProductErrorCode.UPDATE_CONFLICT
                                        )
                );
    }

    @Test
    void updateProductDeletedDuringUpdateThrowsNotFoundException() {
        ProductForm form = validProductForm();

        when(productMapper.findAdminProductFormById(1L))
                .thenReturn(
                        validProductForm(),
                        (ProductForm) null
                );
        when(productMapper.existsActiveCategoryById(1L))
                .thenReturn(true);
        when(productMapper.updateProduct(
                any(Product.class),
                any()
        ))
                .thenReturn(0);

        assertThatThrownBy(() ->
                productAdminService.updateProduct(
                        1L,
                        form
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

    /**
     * 상품 등록 테스트에 사용할 정상 입력값을 만든다.
     */
    private Product productWithStatus(ProductStatus status) {
        Product product = new Product();

        product.setId(1L);
        product.setStatus(status);

        return product;
    }

    private ProductOptionAdminRow requiredGroupRow(
            Long optionId,
            ProductOptionStatus optionStatus
    ) {
        return new ProductOptionAdminRow(
                10L,
                "크기",
                true,
                ProductOptionSelectionType.SINGLE,
                ProductOptionStatus.ACTIVE,
                1,
                optionId,
                optionId == null ? null : "1호",
                optionId == null ? null : BigDecimal.ZERO,
                optionStatus,
                optionId == null ? null : 1
        );
    }

    private ProductForm validProductForm() {
        ProductForm form = new ProductForm();

        form.setCategoryId(1L);
        form.setName("  신규 케이크  ");
        form.setDescription("   ");
        form.setBasePrice(
                BigDecimal.valueOf(35_000)
        );
        form.setStockQuantity(10);
        form.setOriginalStockQuantity(10);
        form.setProductType(ProductType.GENERAL);
        form.setPreparationDays(0);

        return form;
    }
}
