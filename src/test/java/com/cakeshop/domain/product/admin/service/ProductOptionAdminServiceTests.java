package com.cakeshop.domain.product.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;

import com.cakeshop.domain.product.admin.dto.form.ProductForm;
import com.cakeshop.domain.product.admin.dto.form.ProductOptionForm;
import com.cakeshop.domain.product.admin.dto.form.ProductOptionGroupForm;
import com.cakeshop.domain.product.admin.dto.form.ProductOptionMoveDirection;
import com.cakeshop.domain.product.admin.dto.view.ProductOptionAdminRow;
import com.cakeshop.domain.product.admin.dto.view.ProductOptionManagementView;
import com.cakeshop.domain.product.entity.ProductOption;
import com.cakeshop.domain.product.entity.ProductOptionGroup;
import com.cakeshop.domain.product.entity.ProductOptionSelectionType;
import com.cakeshop.domain.product.entity.ProductOptionStatus;
import com.cakeshop.domain.product.error.ProductErrorCode;
import com.cakeshop.domain.product.mapper.ProductMapper;
import com.cakeshop.global.error.BusinessException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProductOptionAdminServiceTests {

    @Mock
    private ProductMapper productMapper;

    @InjectMocks
    private ProductOptionAdminService productOptionAdminService;

    @Test
    void getOptions_existingProduct_groupsOptionRows() {
        ProductForm product = new ProductForm();
        product.setName("레터링 케이크");

        when(productMapper.findAdminProductFormById(1L))
                .thenReturn(product);
        when(productMapper.findAdminOptionRowsByProductId(1L))
                .thenReturn(List.of(
                        optionRow(11L, "1호", 1),
                        optionRow(12L, "2호", 2)
                ));

        ProductOptionManagementView result =
                productOptionAdminService.getOptions(1L);

        assertThat(result.productId()).isEqualTo(1L);
        assertThat(result.productName())
                .isEqualTo("레터링 케이크");
        assertThat(result.optionGroups()).hasSize(1);
        assertThat(result.optionGroups().getFirst().options())
                .extracting(option -> option.name())
                .containsExactly("1호", "2호");
    }

    @Test
    void getOptions_missingProduct_throwsNotFound() {
        when(productMapper.findAdminProductFormById(999L))
                .thenReturn(null);

        assertThatThrownBy(() ->
                productOptionAdminService.getOptions(999L)
        ).isInstanceOfSatisfying(
                BusinessException.class,
                exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(
                                        ProductErrorCode.NOT_FOUND
                                )
        );

        verify(productMapper, never())
                .findAdminOptionRowsByProductId(anyLong());
    }

    @Test
    void createOptionGroup_validForm_insertsNormalizedGroup() {
        when(productMapper.findAdminProductFormById(1L))
                .thenReturn(new ProductForm());
        doAnswer(invocation -> {
            ProductOptionGroup optionGroup =
                    invocation.getArgument(0);
            optionGroup.setId(10L);
            return 1;
        }).when(productMapper).insertOptionGroup(
                any(ProductOptionGroup.class)
        );

        long optionGroupId =
                productOptionAdminService.createOptionGroup(
                        1L,
                        optionGroupForm()
                );

        ArgumentCaptor<ProductOptionGroup> captor =
                ArgumentCaptor.forClass(
                        ProductOptionGroup.class
                );

        verify(productMapper)
                .insertOptionGroup(captor.capture());

        ProductOptionGroup saved = captor.getValue();

        assertThat(optionGroupId).isEqualTo(10L);
        assertThat(saved.getProductId()).isEqualTo(1L);
        assertThat(saved.getName()).isEqualTo("크기");
        assertThat(saved.isRequired()).isTrue();
        assertThat(saved.getStatus())
                .isEqualTo(ProductOptionStatus.ACTIVE);
        assertThat(saved.getSortOrder()).isEqualTo(1);
    }

    @Test
    void updateOptionGroup_otherProductGroup_throwsNotFound() {
        when(productMapper.findAdminProductFormById(1L))
                .thenReturn(new ProductForm());
        when(productMapper.existsOptionGroupById(1L, 99L))
                .thenReturn(false);

        assertThatThrownBy(() ->
                productOptionAdminService.updateOptionGroup(
                        1L,
                        99L,
                        optionGroupForm()
                )
        ).isInstanceOfSatisfying(
                BusinessException.class,
                exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(
                                        ProductErrorCode
                                                .OPTION_GROUP_NOT_FOUND
                                )
        );

        verify(productMapper, never())
                .updateOptionGroup(
                        anyLong(),
                        any(ProductOptionGroup.class)
                );
    }

    @Test
    void createOption_validForm_insertsNormalizedOption() {
        when(productMapper.findAdminProductFormById(1L))
                .thenReturn(new ProductForm());
        when(productMapper.existsOptionGroupById(1L, 10L))
                .thenReturn(true);
        when(productMapper.findAdminOptionRowsByProductId(1L))
                .thenReturn(List.of(
                        optionRow(11L, "1호", 2)
                ));
        doAnswer(invocation -> {
            ProductOption option = invocation.getArgument(0);
            option.setId(20L);
            return 1;
        }).when(productMapper).insertProductOption(
                any(ProductOption.class)
        );

        long optionId =
                productOptionAdminService.createOption(
                        1L,
                        10L,
                        optionForm()
                );

        ArgumentCaptor<ProductOption> captor =
                ArgumentCaptor.forClass(ProductOption.class);

        verify(productMapper)
                .insertProductOption(captor.capture());

        ProductOption saved = captor.getValue();

        assertThat(optionId).isEqualTo(20L);
        assertThat(saved.getOptionGroupId()).isEqualTo(10L);
        assertThat(saved.getName()).isEqualTo("2호");
        assertThat(saved.getAdditionalPrice())
                .isEqualByComparingTo("10000");
        assertThat(saved.getSortOrder()).isEqualTo(3);
    }

    @Test
    void updateOption_otherProductOption_throwsNotFound() {
        when(productMapper.existsProductOptionById(
                1L,
                10L,
                99L
        )).thenReturn(false);

        assertThatThrownBy(() ->
                productOptionAdminService.updateOption(
                        1L,
                        10L,
                        99L,
                        optionForm()
                )
        ).isInstanceOfSatisfying(
                BusinessException.class,
                exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(
                                        ProductErrorCode.OPTION_NOT_FOUND
                                )
        );

        verify(productMapper, never())
                .updateProductOption(
                        anyLong(),
                        anyLong(),
                        any(ProductOption.class)
                );
    }

    @Test
    void moveOptionGroup_down_swapsAndNormalizesSortOrder() {
        when(productMapper.findAdminProductFormById(1L))
                .thenReturn(new ProductForm());
        when(productMapper.existsOptionGroupById(1L, 10L))
                .thenReturn(true);
        when(productMapper.findAdminOptionRowsByProductId(1L))
                .thenReturn(List.of(
                        groupRow(10L, "크기", 2),
                        groupRow(20L, "맛", 2)
                ));

        productOptionAdminService.moveOptionGroup(
                1L,
                10L,
                ProductOptionMoveDirection.DOWN
        );

        ArgumentCaptor<Long> idCaptor =
                ArgumentCaptor.forClass(Long.class);
        ArgumentCaptor<Integer> orderCaptor =
                ArgumentCaptor.forClass(Integer.class);

        verify(productMapper, times(2))
                .updateOptionGroupSortOrder(
                        org.mockito.ArgumentMatchers.eq(1L),
                        idCaptor.capture(),
                        orderCaptor.capture()
                );

        assertThat(idCaptor.getAllValues())
                .containsExactly(20L, 10L);
        assertThat(orderCaptor.getAllValues())
                .containsExactly(1, 2);
    }

    @Test
    void moveOption_up_swapsAndNormalizesSortOrder() {
        when(productMapper.existsProductOptionById(
                1L,
                10L,
                12L
        )).thenReturn(true);
        when(productMapper.findAdminOptionRowsByProductId(1L))
                .thenReturn(List.of(
                        optionRow(11L, "1호", 1),
                        optionRow(12L, "2호", 1)
                ));

        productOptionAdminService.moveOption(
                1L,
                10L,
                12L,
                ProductOptionMoveDirection.UP
        );

        ArgumentCaptor<Long> idCaptor =
                ArgumentCaptor.forClass(Long.class);
        ArgumentCaptor<Integer> orderCaptor =
                ArgumentCaptor.forClass(Integer.class);

        verify(productMapper, times(2))
                .updateProductOptionSortOrder(
                        org.mockito.ArgumentMatchers.eq(1L),
                        org.mockito.ArgumentMatchers.eq(10L),
                        idCaptor.capture(),
                        orderCaptor.capture()
                );

        assertThat(idCaptor.getAllValues())
                .containsExactly(12L, 11L);
        assertThat(orderCaptor.getAllValues())
                .containsExactly(1, 2);
    }

    private ProductOptionAdminRow optionRow(
            long optionId,
            String optionName,
            int optionSortOrder
    ) {
        return new ProductOptionAdminRow(
                10L,
                "크기",
                true,
                ProductOptionSelectionType.SINGLE,
                ProductOptionStatus.ACTIVE,
                1,
                optionId,
                optionName,
                BigDecimal.ZERO,
                ProductOptionStatus.ACTIVE,
                optionSortOrder
        );
    }

    private ProductOptionAdminRow groupRow(
            long groupId,
            String groupName,
            int groupSortOrder
    ) {
        return new ProductOptionAdminRow(
                groupId,
                groupName,
                true,
                ProductOptionSelectionType.SINGLE,
                ProductOptionStatus.ACTIVE,
                groupSortOrder,
                null,
                null,
                null,
                null,
                null
        );
    }

    private ProductOptionGroupForm optionGroupForm() {
        ProductOptionGroupForm form =
                new ProductOptionGroupForm();

        form.setName("  크기  ");
        form.setRequired(true);
        form.setSelectionType(
                ProductOptionSelectionType.SINGLE
        );
        form.setStatus(ProductOptionStatus.ACTIVE);

        return form;
    }

    private ProductOptionForm optionForm() {
        ProductOptionForm form = new ProductOptionForm();

        form.setName("  2호  ");
        form.setAdditionalPrice(
                BigDecimal.valueOf(10_000)
        );
        form.setStatus(ProductOptionStatus.ACTIVE);

        return form;
    }
}
