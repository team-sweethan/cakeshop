package com.cakeshop.domain.cart.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;

import com.cakeshop.domain.cart.dto.form.CartAddForm;
import com.cakeshop.domain.cart.dto.form.CartUpdateForm;
import com.cakeshop.domain.cart.dto.view.CartItemView;
import com.cakeshop.domain.cart.dto.view.CartCountView;
import com.cakeshop.domain.cart.dto.view.CartView;
import com.cakeshop.domain.cart.dto.view.CartQuantityUpdateView;
import com.cakeshop.domain.cart.error.CartErrorCode;
import com.cakeshop.domain.cart.service.CartService;
import com.cakeshop.domain.member.dto.view.MemberAuthenticationView;
import com.cakeshop.domain.product.entity.ProductType;
import com.cakeshop.global.security.MemberDetails;
import com.cakeshop.global.error.BusinessException;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@ExtendWith(MockitoExtension.class)
class CartControllerTests {

    @Mock
    private CartService cartService;

    @Mock
    private Model model;

    @Mock
    private BindingResult bindingResult;

    @Mock
    private RedirectAttributes redirectAttributes;

    @InjectMocks
    private CartController cartController;

    @Test
    void cart_authenticatedMember_loadsOwnCart() {
        MemberDetails member = memberDetails();
        CartView cart = new CartView(
                List.of(), 0, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        when(cartService.getCart(1L)).thenReturn(cart);

        String viewName = cartController.cart(member, model);

        assertThat(viewName).isEqualTo("customer/cart/list");
        verify(model).addAttribute("cart", cart);
    }

    @Test
    void addItem_validForm_addsForAuthenticatedMember() {
        MemberDetails member = memberDetails();
        CartAddForm form = new CartAddForm();
        form.setProductId(10L);
        form.setQuantity(2);
        when(bindingResult.hasErrors()).thenReturn(false);

        String viewName = cartController.addItem(
                member, form, bindingResult, redirectAttributes);

        assertThat(viewName).isEqualTo("redirect:/products?type=GENERAL");
        verify(cartService).addItem(1L, form);
    }

    @Test
    void addItem_staleProduct_redirectsToDetailWithErrorMessage() {
        MemberDetails member = memberDetails();
        CartAddForm form = new CartAddForm();
        form.setProductId(10L);
        form.setQuantity(2);
        when(bindingResult.hasErrors()).thenReturn(false);
        doThrow(new BusinessException(CartErrorCode.OUT_OF_STOCK))
                .when(cartService).addItem(1L, form);

        String viewName = cartController.addItem(
                member, form, bindingResult, redirectAttributes);

        assertThat(viewName).isEqualTo("redirect:/products/10");
        verify(redirectAttributes).addFlashAttribute(
                "errorMessage", CartErrorCode.OUT_OF_STOCK.message());
    }

    @Test
    void addItem_stoppedProduct_redirectsToListWithErrorMessage() {
        MemberDetails member = memberDetails();
        CartAddForm form = new CartAddForm();
        form.setProductId(10L);
        form.setQuantity(2);
        when(bindingResult.hasErrors()).thenReturn(false);
        doThrow(new BusinessException(CartErrorCode.PRODUCT_NOT_ON_SALE))
                .when(cartService).addItem(1L, form);

        String viewName = cartController.addItem(
                member, form, bindingResult, redirectAttributes);

        assertThat(viewName).isEqualTo("redirect:/products");
        verify(redirectAttributes).addFlashAttribute(
                "errorMessage", CartErrorCode.PRODUCT_NOT_ON_SALE.message());
    }

    @Test
    void updateQuantityAsync_validForm_returnsUpdatedTotals() {
        MemberDetails member = memberDetails();
        CartUpdateForm form = new CartUpdateForm();
        form.setQuantity(3);
        CartItemView item = new CartItemView(
                30L, 10L, "cake", ProductType.GENERAL, 3, 10, true,
                null,
                BigDecimal.valueOf(35000), BigDecimal.ZERO, BigDecimal.valueOf(105000),
                null, List.of());
        CartView cart = new CartView(
                List.of(item), 1, BigDecimal.valueOf(105000),
                BigDecimal.ZERO, BigDecimal.valueOf(105000));
        when(cartService.getCart(1L)).thenReturn(cart);

        CartQuantityUpdateView result = cartController.updateQuantityAsync(member, 30L, form);

        verify(cartService).updateQuantity(1L, 30L, 3);
        assertThat(result.itemTotal()).isEqualByComparingTo("105000");
        assertThat(result.itemCount()).isEqualTo(1);
        assertThat(result.available()).isTrue();
        assertThat(result.itemAvailability()).hasSize(1);
        assertThat(result.itemAvailability().getFirst().itemId()).isEqualTo(30L);
        assertThat(result.itemAvailability().getFirst().available()).isTrue();
    }

    @Test
    void updateQuantity_staleCart_redirectsWithErrorMessage() {
        MemberDetails member = memberDetails();
        CartUpdateForm form = new CartUpdateForm();
        form.setQuantity(2);
        when(bindingResult.hasErrors()).thenReturn(false);
        doThrow(new BusinessException(CartErrorCode.OUT_OF_STOCK))
                .when(cartService).updateQuantity(1L, 30L, 2);

        String viewName = cartController.updateQuantity(
                member, 30L, form, bindingResult, redirectAttributes);

        assertThat(viewName).isEqualTo("redirect:/cart");
        verify(redirectAttributes).addFlashAttribute(
                "errorMessage", CartErrorCode.OUT_OF_STOCK.message());
    }

    @Test
    void deleteItem_staleCart_redirectsWithErrorMessage() {
        MemberDetails member = memberDetails();
        doThrow(new BusinessException(CartErrorCode.ITEM_NOT_FOUND))
                .when(cartService).deleteItem(1L, 30L);

        String viewName = cartController.deleteItem(member, 30L, redirectAttributes);

        assertThat(viewName).isEqualTo("redirect:/cart");
        verify(redirectAttributes).addFlashAttribute(
                "errorMessage", CartErrorCode.ITEM_NOT_FOUND.message());
    }

    @Test
    void count_authenticatedMember_returnsCartItemCount() {
        MemberDetails member = memberDetails();
        when(cartService.getItemCount(1L)).thenReturn(3);

        CartCountView result = cartController.count(member);

        assertThat(result.itemCount()).isEqualTo(3);
    }

    private MemberDetails memberDetails() {
        return new MemberDetails(new MemberAuthenticationView(
                1L,
                "user@cakeshop.local",
                "encoded-password",
                "USER",
                true));
    }
}
