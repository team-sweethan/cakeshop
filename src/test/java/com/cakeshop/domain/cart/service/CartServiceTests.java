package com.cakeshop.domain.cart.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cakeshop.domain.cart.dto.form.CartAddForm;
import com.cakeshop.domain.cart.dto.view.CartView;
import com.cakeshop.domain.cart.entity.CartItem;
import com.cakeshop.domain.cart.entity.CartItemOption;
import com.cakeshop.domain.cart.error.CartErrorCode;
import com.cakeshop.domain.cart.mapper.CartMapper;
import com.cakeshop.domain.product.dto.view.ProductOptionGroupView;
import com.cakeshop.domain.product.dto.view.ProductOptionItemView;
import com.cakeshop.domain.product.dto.view.ProductSalesInfo;
import com.cakeshop.domain.product.entity.ProductType;
import com.cakeshop.domain.product.service.ProductQueryService;
import com.cakeshop.domain.product.service.ProductService;
import com.cakeshop.global.error.BusinessException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CartServiceTests {

    @Mock
    private CartMapper cartMapper;

    @Mock
    private ProductQueryService productQueryService;

    @Mock
    private ProductService productService;

    @InjectMocks
    private CartService cartService;

    @Test
    void addItem_newConfiguration_insertsValidatedSnapshots() {
        CartAddForm form = form(10L, 2, List.of(101L));
        when(productQueryService.getSalesInfo(10L)).thenReturn(product(10));
        when(productService.getPublicOptionGroups(10L)).thenReturn(requiredOptions());
        when(cartMapper.findCartIdByMemberIdForUpdate(1L)).thenReturn(Optional.of(20L));
        when(cartMapper.findItemsByMemberId(1L)).thenReturn(List.of());
        when(cartMapper.insertItem(org.mockito.ArgumentMatchers.any())).thenAnswer(invocation -> {
            CartItem item = invocation.getArgument(0);
            item.setId(30L);
            return 1;
        });
        when(cartMapper.insertItemOption(org.mockito.ArgumentMatchers.any())).thenReturn(1);

        cartService.addItem(1L, form);

        ArgumentCaptor<CartItem> itemCaptor = ArgumentCaptor.forClass(CartItem.class);
        verify(cartMapper).insertItem(itemCaptor.capture());
        org.assertj.core.api.Assertions.assertThat(itemCaptor.getValue().getCartId()).isEqualTo(20L);
        org.assertj.core.api.Assertions.assertThat(itemCaptor.getValue().getQuantity()).isEqualTo(2);
        ArgumentCaptor<CartItemOption> optionCaptor = ArgumentCaptor.forClass(CartItemOption.class);
        verify(cartMapper).insertItemOption(optionCaptor.capture());
        org.assertj.core.api.Assertions.assertThat(optionCaptor.getValue().getCartItemId()).isEqualTo(30L);
        org.assertj.core.api.Assertions.assertThat(optionCaptor.getValue().getOptionName()).isEqualTo("초코");
    }

    @Test
    void addItem_sameProductAndOptions_mergesQuantity() {
        CartAddForm form = form(10L, 2, List.of(101L));
        CartItem existing = item(30L, 10L, 3);
        CartItemOption option = option(30L, 101L);
        when(productQueryService.getSalesInfo(10L)).thenReturn(product(10));
        when(productService.getPublicOptionGroups(10L)).thenReturn(requiredOptions());
        when(cartMapper.findCartIdByMemberIdForUpdate(1L)).thenReturn(Optional.of(20L));
        when(cartMapper.findItemsByMemberId(1L)).thenReturn(List.of(existing));
        when(cartMapper.findOptionsByCartItemIds(List.of(30L))).thenReturn(List.of(option));
        when(cartMapper.findItemByMemberIdAndItemId(1L, 30L)).thenReturn(Optional.of(existing));
        when(cartMapper.updateItemQuantity(1L, 30L, 5)).thenReturn(1);

        cartService.addItem(1L, form);

        verify(cartMapper).updateItemQuantity(1L, 30L, 5);
        verify(cartMapper, never()).insertItem(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void addItem_sameOptionIdButChangedSnapshot_insertsSeparateItem() {
        CartAddForm form = form(10L, 2, List.of(101L));
        CartItem existing = item(30L, 10L, 3);
        CartItemOption oldSnapshot = option(30L, 101L);
        oldSnapshot.setAdditionalPrice(BigDecimal.valueOf(1000));
        when(productQueryService.getSalesInfo(10L)).thenReturn(product(10));
        when(productService.getPublicOptionGroups(10L)).thenReturn(requiredOptions());
        when(cartMapper.findCartIdByMemberIdForUpdate(1L)).thenReturn(Optional.of(20L));
        when(cartMapper.findItemsByMemberId(1L)).thenReturn(List.of(existing));
        when(cartMapper.findOptionsByCartItemIds(List.of(30L))).thenReturn(List.of(oldSnapshot));
        when(cartMapper.insertItem(org.mockito.ArgumentMatchers.any())).thenAnswer(invocation -> {
            CartItem item = invocation.getArgument(0);
            item.setId(31L);
            return 1;
        });
        when(cartMapper.insertItemOption(org.mockito.ArgumentMatchers.any())).thenReturn(1);

        cartService.addItem(1L, form);

        verify(cartMapper).insertItem(org.mockito.ArgumentMatchers.any());
        verify(cartMapper, never()).updateItemQuantity(1L, 30L, 5);
    }

    @Test
    void addItem_sameProductAndOptionsButDifferentRequirements_insertsSeparateItem() {
        CartAddForm form = form(10L, 2, List.of(101L));
        form.setRequirements("new message");
        CartItem existing = item(30L, 10L, 3);
        existing.setRequirements("old message");
        when(productQueryService.getSalesInfo(10L)).thenReturn(product(10));
        when(productService.getPublicOptionGroups(10L)).thenReturn(requiredOptions());
        when(cartMapper.findCartIdByMemberIdForUpdate(1L)).thenReturn(Optional.of(20L));
        when(cartMapper.findItemsByMemberId(1L)).thenReturn(List.of(existing));
        when(cartMapper.findOptionsByCartItemIds(List.of(30L)))
                .thenReturn(List.of(option(30L, 101L)));
        when(cartMapper.insertItem(org.mockito.ArgumentMatchers.any())).thenAnswer(invocation -> {
            CartItem item = invocation.getArgument(0);
            item.setId(31L);
            return 1;
        });
        when(cartMapper.insertItemOption(org.mockito.ArgumentMatchers.any())).thenReturn(1);

        cartService.addItem(1L, form);

        verify(cartMapper).insertItem(org.mockito.ArgumentMatchers.any());
        verify(cartMapper, never()).updateItemQuantity(1L, 30L, 5);
    }

    @Test
    void getCart_unavailableItem_excludesItemFromSummaryTotals() {
        CartItem item = item(30L, 10L, 2);
        when(cartMapper.findItemsByMemberId(1L)).thenReturn(List.of(item));
        when(cartMapper.findOptionsByCartItemIds(List.of(30L)))
                .thenReturn(List.of(option(30L, 101L)));
        when(productQueryService.getSalesInfo(10L))
                .thenThrow(new BusinessException(CartErrorCode.PRODUCT_NOT_ON_SALE));

        CartView cart = cartService.getCart(1L);

        assertThat(cart.items().getFirst().available()).isFalse();
        assertThat(cart.baseTotal()).isZero();
        assertThat(cart.optionTotal()).isZero();
        assertThat(cart.grandTotal()).isZero();
    }

    @Test
    void getCart_inactiveOption_excludesItemFromSummaryTotals() {
        CartItem item = item(30L, 10L, 2);
        when(cartMapper.findItemsByMemberId(1L)).thenReturn(List.of(item));
        when(cartMapper.findOptionsByCartItemIds(List.of(30L)))
                .thenReturn(List.of(option(30L, 101L)));
        when(productQueryService.getSalesInfo(10L)).thenReturn(product(10));
        when(productService.getPublicOptionGroups(10L)).thenReturn(List.of());

        CartView cart = cartService.getCart(1L);

        assertThat(cart.items().getFirst().available()).isFalse();
        assertThat(cart.baseTotal()).isZero();
        assertThat(cart.optionTotal()).isZero();
        assertThat(cart.grandTotal()).isZero();
    }

    @Test
    void addItem_requiredOptionMissing_throwsBusinessException() {
        CartAddForm form = form(10L, 1, List.of());
        when(productQueryService.getSalesInfo(10L)).thenReturn(product(10));
        when(productService.getPublicOptionGroups(10L)).thenReturn(requiredOptions());

        assertThatThrownBy(() -> cartService.addItem(1L, form))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(CartErrorCode.REQUIRED_OPTION_MISSING);

        verify(cartMapper, never()).insertItem(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void updateQuantity_otherMembersItem_throwsNotFound() {
        when(cartMapper.findCartIdByMemberIdForUpdate(1L)).thenReturn(Optional.of(20L));
        when(cartMapper.findItemByMemberIdAndItemId(1L, 99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> cartService.updateQuantity(1L, 99L, 2))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(CartErrorCode.ITEM_NOT_FOUND);

        verify(productQueryService, never()).getSalesInfo(org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void updateQuantity_ownedItem_locksCartBeforeReadingQuantity() {
        CartItem item = item(30L, 10L, 3);
        when(cartMapper.findCartIdByMemberIdForUpdate(1L)).thenReturn(Optional.of(20L));
        when(cartMapper.findItemByMemberIdAndItemId(1L, 30L)).thenReturn(Optional.of(item));
        when(productQueryService.getSalesInfo(10L)).thenReturn(product(10));
        when(cartMapper.updateItemQuantity(1L, 30L, 2)).thenReturn(1);

        cartService.updateQuantity(1L, 30L, 2);

        InOrder inOrder = inOrder(cartMapper);
        inOrder.verify(cartMapper).findCartIdByMemberIdForUpdate(1L);
        inOrder.verify(cartMapper).findItemByMemberIdAndItemId(1L, 30L);
        inOrder.verify(cartMapper).updateItemQuantity(1L, 30L, 2);
    }

    @Test
    void updateQuantity_inactiveOption_throwsInvalidOption() {
        CartItem item = item(30L, 10L, 3);
        when(cartMapper.findCartIdByMemberIdForUpdate(1L)).thenReturn(Optional.of(20L));
        when(cartMapper.findItemByMemberIdAndItemId(1L, 30L)).thenReturn(Optional.of(item));
        when(productQueryService.getSalesInfo(10L)).thenReturn(product(10));
        when(cartMapper.findOptionsByCartItemIds(List.of(30L)))
                .thenReturn(List.of(option(30L, 101L)));
        when(productService.getPublicOptionGroups(10L)).thenReturn(List.of());

        assertThatThrownBy(() -> cartService.updateQuantity(1L, 30L, 2))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(CartErrorCode.INVALID_OPTION);

        verify(cartMapper, never()).updateItemQuantity(1L, 30L, 2);
    }

    @Test
    void deleteSelectedItems_existingCart_locksBeforeDeleting() {
        when(cartMapper.findCartIdByMemberIdForUpdate(1L)).thenReturn(Optional.of(20L));
        when(cartMapper.deleteItemsByMemberIdAndItemIds(1L, List.of(30L))).thenReturn(1);

        cartService.deleteSelectedItems(1L, List.of(30L));

        InOrder inOrder = inOrder(cartMapper);
        inOrder.verify(cartMapper).findCartIdByMemberIdForUpdate(1L);
        inOrder.verify(cartMapper).deleteOptionsByMemberIdAndItemIds(1L, List.of(30L));
        inOrder.verify(cartMapper).deleteImagesByMemberIdAndItemIds(1L, List.of(30L));
        inOrder.verify(cartMapper).deleteItemsByMemberIdAndItemIds(1L, List.of(30L));
    }

    @Test
    void clearCart_existingCart_locksBeforeDeleting() {
        when(cartMapper.findCartIdByMemberIdForUpdate(1L)).thenReturn(Optional.of(20L));

        cartService.clearCart(1L);

        InOrder inOrder = inOrder(cartMapper);
        inOrder.verify(cartMapper).findCartIdByMemberIdForUpdate(1L);
        inOrder.verify(cartMapper).deleteAllOptionsByMemberId(1L);
        inOrder.verify(cartMapper).deleteAllImagesByMemberId(1L);
        inOrder.verify(cartMapper).deleteAllItemsByMemberId(1L);
    }

    private CartAddForm form(long productId, int quantity, List<Long> optionIds) {
        CartAddForm form = new CartAddForm();
        form.setProductId(productId);
        form.setQuantity(quantity);
        form.setOptionIds(optionIds);
        return form;
    }

    private ProductSalesInfo product(int stock) {
        return new ProductSalesInfo(
                10L,
                "딸기 케이크",
                ProductType.GENERAL,
                0,
                true,
                BigDecimal.valueOf(30000),
                stock);
    }

    private List<ProductOptionGroupView> requiredOptions() {
        return List.of(new ProductOptionGroupView(
                100L,
                "맛",
                true,
                "SINGLE",
                List.of(new ProductOptionItemView(101L, "초코", BigDecimal.valueOf(2000)))));
    }

    private CartItem item(long id, long productId, int quantity) {
        CartItem item = new CartItem();
        item.setId(id);
        item.setProductId(productId);
        item.setQuantity(quantity);
        return item;
    }

    private CartItemOption option(long itemId, long optionId) {
        CartItemOption option = new CartItemOption();
        option.setCartItemId(itemId);
        option.setProductOptionId(optionId);
        option.setOptionName("초코");
        option.setAdditionalPrice(BigDecimal.valueOf(2000));
        return option;
    }
}
