package com.cakeshop.domain.cart.controller;

import com.cakeshop.domain.cart.dto.form.CartAddForm;
import com.cakeshop.domain.cart.dto.form.CartDeleteSelectedForm;
import com.cakeshop.domain.cart.dto.form.CartUpdateForm;
import com.cakeshop.domain.cart.dto.view.CartCountView;
import com.cakeshop.domain.cart.dto.view.CartQuantityUpdateView;
import com.cakeshop.domain.cart.error.CartErrorCode;
import com.cakeshop.domain.cart.service.CartService;
import com.cakeshop.global.error.BusinessException;
import com.cakeshop.global.security.MemberDetails;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/cart")
public class CartController {

    private final CartService cartService;

    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    @GetMapping
    public String cart(
            @AuthenticationPrincipal MemberDetails member,
            Model model
    ) {
        model.addAttribute("cart", cartService.getCart(member.getMemberId()));
        model.addAttribute("deleteSelectedForm", new CartDeleteSelectedForm());
        return "customer/cart/list";
    }

    @GetMapping("/continue/{productId}")
    public String continueAfterLogin(@PathVariable long productId) {
        return "redirect:/products/" + productId;
    }

    @PostMapping("/items")
    public String addItem(
            @AuthenticationPrincipal MemberDetails member,
            @Valid @ModelAttribute CartAddForm form,
            BindingResult bindingResult,
            RedirectAttributes redirectAttributes
    ) {
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute(
                    "errorMessage",
                    "상품과 수량을 다시 확인해 주세요.");
            return "redirect:/products/" + safeProductId(form.getProductId());
        }
        try {
            cartService.addItem(member.getMemberId(), form);
        } catch (BusinessException exception) {
            redirectAttributes.addFlashAttribute(
                    "errorMessage",
                    exception.getErrorCode().message());
            if (exception.getErrorCode() == CartErrorCode.PRODUCT_NOT_ON_SALE) {
                return "redirect:/products";
            }
            return "redirect:/products/" + form.getProductId();
        }
//        redirectAttributes.addFlashAttribute("successMessage", "장바구니에 담았습니다.");
        return "redirect:/products?type=GENERAL";
    }

    @PostMapping("/items/{itemId}/quantity")
    public String updateQuantity(
            @AuthenticationPrincipal MemberDetails member,
            @PathVariable long itemId,
            @Valid @ModelAttribute CartUpdateForm form,
            BindingResult bindingResult,
            RedirectAttributes redirectAttributes
    ) {
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("errorMessage", "수량은 1개 이상이어야 합니다.");
            return "redirect:/cart";
        }
        try {
            cartService.updateQuantity(member.getMemberId(), itemId, form.getQuantity());
        } catch (BusinessException exception) {
            redirectAttributes.addFlashAttribute(
                    "errorMessage",
                    exception.getErrorCode().message());
            return "redirect:/cart";
        }
        redirectAttributes.addFlashAttribute("successMessage", "수량을 변경했습니다.");
        return "redirect:/cart";
    }

    @PostMapping("/items/{itemId}/quantity/async")
    @ResponseBody
    public CartQuantityUpdateView updateQuantityAsync(
            @AuthenticationPrincipal MemberDetails member,
            @PathVariable long itemId,
            @Valid @ModelAttribute CartUpdateForm form
    ) {
        cartService.updateQuantity(member.getMemberId(), itemId, form.getQuantity());
        return CartQuantityUpdateView.from(cartService.getCart(member.getMemberId()), itemId);
    }

    @GetMapping("/count")
    @ResponseBody
    public CartCountView count(@AuthenticationPrincipal MemberDetails member) {
        return new CartCountView(cartService.getItemCount(member.getMemberId()));
    }

    @PostMapping("/items/{itemId}/delete")
    public String deleteItem(
            @AuthenticationPrincipal MemberDetails member,
            @PathVariable long itemId,
            RedirectAttributes redirectAttributes
    ) {
        try {
            cartService.deleteItem(member.getMemberId(), itemId);
        } catch (BusinessException exception) {
            redirectAttributes.addFlashAttribute(
                    "errorMessage",
                    exception.getErrorCode().message());
            return "redirect:/cart";
        }
        redirectAttributes.addFlashAttribute("successMessage", "장바구니 상품을 삭제했습니다.");
        return "redirect:/cart";
    }

    @PostMapping("/items/delete-selected")
    public String deleteSelectedItems(
            @AuthenticationPrincipal MemberDetails member,
            @Valid @ModelAttribute CartDeleteSelectedForm form,
            BindingResult bindingResult,
            RedirectAttributes redirectAttributes
    ) {
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("errorMessage", "삭제할 상품을 선택해 주세요.");
            return "redirect:/cart";
        }
        try {
            cartService.deleteSelectedItems(member.getMemberId(), form.getItemIds());
        } catch (BusinessException exception) {
            redirectAttributes.addFlashAttribute(
                    "errorMessage",
                    exception.getErrorCode().message());
            return "redirect:/cart";
        }
        redirectAttributes.addFlashAttribute("successMessage", "선택한 상품을 삭제했습니다.");
        return "redirect:/cart";
    }

    @PostMapping("/items/delete-all")
    public String clearCart(
            @AuthenticationPrincipal MemberDetails member,
            RedirectAttributes redirectAttributes
    ) {
        try {
            cartService.clearCart(member.getMemberId());
        } catch (BusinessException exception) {
            redirectAttributes.addFlashAttribute(
                    "errorMessage",
                    exception.getErrorCode().message());
            return "redirect:/cart";
        }
        redirectAttributes.addFlashAttribute("successMessage", "장바구니를 비웠습니다.");
        return "redirect:/cart";
    }

    private long safeProductId(Long productId) {
        return productId == null || productId < 1 ? 1 : productId;
    }
}
