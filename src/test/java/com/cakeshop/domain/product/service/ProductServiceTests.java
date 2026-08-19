package com.cakeshop.domain.product.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;

import com.cakeshop.domain.product.dto.form.ProductSearchCondition;
import com.cakeshop.domain.product.dto.form.ProductSort;
import com.cakeshop.domain.product.dto.view.ProductDetailView;
import com.cakeshop.domain.product.dto.view.ProductImageView;
import com.cakeshop.domain.product.dto.view.ProductListView;
import com.cakeshop.domain.product.dto.view.ProductOptionGroupView;
import com.cakeshop.domain.product.dto.view.ProductOptionRow;
import com.cakeshop.domain.product.entity.ProductImage;
import com.cakeshop.domain.product.entity.ProductType;
import com.cakeshop.domain.product.mapper.ProductMapper;
import com.cakeshop.global.common.paging.PageRequest;
import com.cakeshop.global.common.paging.PageResult;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProductServiceTests {

    @Mock
    private ProductMapper productMapper;

    @InjectMocks
    private ProductService productService;

    @Test
    void emptyResultNormalizesConditionAndSkipsListQuery() {
        ProductSearchCondition condition =
                new ProductSearchCondition();

        condition.setKeyword("   ");
        condition.setMinPrice(BigDecimal.valueOf(-1));
        condition.setSort(null);

        when(productMapper.countPublicProducts(any()))
                .thenReturn(0L);

        PageResult<ProductListView> result =
                productService.getPublicProducts(condition, null);

        ArgumentCaptor<ProductSearchCondition> conditionCaptor =
                ArgumentCaptor.forClass(
                        ProductSearchCondition.class
                );

        verify(productMapper)
                .countPublicProducts(conditionCaptor.capture());

        ProductSearchCondition normalized =
                conditionCaptor.getValue();

        assertThat(normalized.getKeyword()).isNull();
        assertThat(normalized.getMinPrice())
                .isEqualByComparingTo("0");
        assertThat(normalized.getMaxPrice()).isNull();
        assertThat(normalized.getSort())
                .isEqualTo(ProductSort.POPULAR);

        verify(productMapper, never())
                .findPublicProducts(
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
    void minimumPriceWithoutMaximumKeepsUpperBoundEmpty() {
        ProductSearchCondition condition =
                new ProductSearchCondition();

        condition.setMinPrice(
                BigDecimal.valueOf(120_000)
        );

        when(productMapper.countPublicProducts(any()))
                .thenReturn(0L);

        productService.getPublicProducts(condition, null);

        ArgumentCaptor<ProductSearchCondition> conditionCaptor =
                ArgumentCaptor.forClass(
                        ProductSearchCondition.class
                );

        verify(productMapper)
                .countPublicProducts(conditionCaptor.capture());

        ProductSearchCondition normalized =
                conditionCaptor.getValue();

        assertThat(normalized.getMinPrice())
                .isEqualByComparingTo("120000");
        assertThat(normalized.getMaxPrice()).isNull();
    }

    @Test
    void negativeMaximumPriceRemovesUpperBound() {
        ProductSearchCondition condition =
                new ProductSearchCondition();

        condition.setMaxPrice(BigDecimal.valueOf(-1));

        when(productMapper.countPublicProducts(any()))
                .thenReturn(0L);

        productService.getPublicProducts(condition, null);

        ArgumentCaptor<ProductSearchCondition> conditionCaptor =
                ArgumentCaptor.forClass(
                        ProductSearchCondition.class
                );

        verify(productMapper)
                .countPublicProducts(conditionCaptor.capture());

        assertThat(conditionCaptor.getValue().getMaxPrice())
                .isNull();
    }

    @Test
    void resultContainsRequestedPageInformation() {
        ProductSearchCondition condition =
                new ProductSearchCondition();

        PageRequest pageRequest = new PageRequest(2, 6);

        ProductListView product = new ProductListView(
                1L,
                "테스트 케이크",
                BigDecimal.valueOf(35_000),
                ProductType.GENERAL,
                10,
                BigDecimal.valueOf(4.8),
                12,
                null
        );

        when(productMapper.countPublicProducts(condition))
                .thenReturn(8L);
        when(productMapper.findPublicProducts(
                condition,
                6,
                6
        )).thenReturn(List.of(product));

        PageResult<ProductListView> result =
                productService.getPublicProducts(
                        condition,
                        pageRequest
                );

        assertThat(result.getContent())
                .containsExactly(product);
        assertThat(result.getPage()).isEqualTo(2);
        assertThat(result.getSize()).isEqualTo(6);
        assertThat(result.getTotalElements()).isEqualTo(8);
        assertThat(result.getTotalPages()).isEqualTo(2);
    }

    @Test
    void getPublicDetail_imagesExist_returnsImageViewsInMapperOrder() {
        ProductDetailView detail = new ProductDetailView();
        detail.setId(1L);

        ProductImage representative = productImage(
                10L,
                1L,
                "/uploads/product/representative.jpg",
                0
        );
        ProductImage second = productImage(
                20L,
                1L,
                "/uploads/product/second.jpg",
                1
        );

        when(productMapper.findPublicDetailById(1L))
                .thenReturn(detail);
        when(productMapper.findProductImagesByProductId(1L))
                .thenReturn(List.of(representative, second));

        ProductDetailView result =
                productService.getPublicDetail(1L);

        assertThat(result.getImages())
                .extracting(ProductImageView::imageUrl)
                .containsExactly(
                        "/uploads/product/representative.jpg",
                        "/uploads/product/second.jpg"
                );
        assertThat(result.getImages())
                .extracting(ProductImageView::sortOrder)
                .containsExactly(0, 1);
    }

    @Test
    void getPublicDetail_imagesMissing_returnsEmptyImageList() {
        ProductDetailView detail = new ProductDetailView();
        detail.setId(1L);

        when(productMapper.findPublicDetailById(1L))
                .thenReturn(detail);
        when(productMapper.findProductImagesByProductId(1L))
                .thenReturn(List.of());

        ProductDetailView result =
                productService.getPublicDetail(1L);

        assertThat(result.getImages()).isEmpty();
    }

    @Test
    void optionRowsAreGroupedInQueryOrder() {
        when(productMapper.findPublicOptionRowsByProductId(1L))
                .thenReturn(List.of(
                        new ProductOptionRow(
                                10L,
                                "케이크 크기",
                                true,
                                "SINGLE",
                                101L,
                                "1호",
                                BigDecimal.ZERO
                        ),
                        new ProductOptionRow(
                                10L,
                                "케이크 크기",
                                true,
                                "SINGLE",
                                102L,
                                "2호",
                                BigDecimal.valueOf(10_000)
                        ),
                        new ProductOptionRow(
                                20L,
                                "추가 장식",
                                false,
                                "MULTIPLE",
                                201L,
                                "별 장식",
                                BigDecimal.valueOf(3_000)
                        )
                ));

        List<ProductOptionGroupView> groups =
                productService.getPublicOptionGroups(1L);

        assertThat(groups)
                .extracting(ProductOptionGroupView::name)
                .containsExactly(
                        "케이크 크기",
                        "추가 장식"
                );

        assertThat(groups.getFirst().required()).isTrue();
        assertThat(groups.getFirst().options())
                .extracting(option -> option.name())
                .containsExactly("1호", "2호");
        assertThat(groups.get(1).required()).isFalse();
        assertThat(groups.get(1).options())
                .extracting(option -> option.name())
                .containsExactly("별 장식");
    }

    @Test
    void reversedPriceRangeIsSwapped() {
        ProductSearchCondition condition =
                new ProductSearchCondition();

        condition.setMinPrice(
                BigDecimal.valueOf(80_000)
        );
        condition.setMaxPrice(
                BigDecimal.valueOf(30_000)
        );

        when(productMapper.countPublicProducts(any()))
                .thenReturn(0L);

        productService.getPublicProducts(
                condition,
                new PageRequest(1, 6)
        );

        ArgumentCaptor<ProductSearchCondition> conditionCaptor =
                ArgumentCaptor.forClass(
                        ProductSearchCondition.class
                );

        verify(productMapper)
                .countPublicProducts(conditionCaptor.capture());

        assertThat(conditionCaptor.getValue().getMinPrice())
                .isEqualByComparingTo("30000");
        assertThat(conditionCaptor.getValue().getMaxPrice())
                .isEqualByComparingTo("80000");
    }

    private ProductImage productImage(
            long id,
            long productId,
            String imageUrl,
            int sortOrder
    ) {
        ProductImage image = new ProductImage();
        image.setId(id);
        image.setProductId(productId);
        image.setImageUrl(imageUrl);
        image.setSortOrder(sortOrder);
        return image;
    }
}
