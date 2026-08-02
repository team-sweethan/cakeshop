package com.cakeshop.domain.cart.service;

import com.cakeshop.domain.cart.dto.form.CartAddForm;
import com.cakeshop.domain.cart.dto.view.CartItemView;
import com.cakeshop.domain.cart.dto.view.CartOptionView;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CartService {

    private final CartMapper cartMapper;
    private final ProductQueryService productQueryService;
    private final ProductService productService;

    public CartService(
            CartMapper cartMapper,
            ProductQueryService productQueryService,
            ProductService productService
    ) {
        this.cartMapper = cartMapper;
        this.productQueryService = productQueryService;
        this.productService = productService;
    }

    @Transactional(readOnly = true)
    public CartView getCart(long memberId) {
        List<CartItem> items = cartMapper.findItemsByMemberId(memberId);
        if (items.isEmpty()) {
            return emptyCart();
        }

        Map<Long, List<CartItemOption>> optionsByItem = optionsByItem(items);
        List<CartItemView> itemViews = items.stream()
                .map(item -> toView(
                        item,
                        optionsByItem.getOrDefault(item.getId(), List.of())))
                .toList();

        BigDecimal baseTotal = itemViews.stream()
                .filter(CartItemView::available)
                .map(item -> item.basePrice().multiply(BigDecimal.valueOf(item.quantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal optionTotal = itemViews.stream()
                .filter(CartItemView::available)
                .map(item -> item.optionPrice().multiply(BigDecimal.valueOf(item.quantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        int totalQuantity = itemViews.stream().mapToInt(CartItemView::quantity).sum();

        return new CartView(
                itemViews,
                totalQuantity,
                baseTotal,
                optionTotal,
                baseTotal.add(optionTotal));
    }

    @Transactional(readOnly = true)
    public int getTotalQuantity(long memberId) {
        return cartMapper.sumQuantityByMemberId(memberId);
    }

    @Transactional
    public void addItem(long memberId, CartAddForm form) {
        ProductSalesInfo product = getAvailableProduct(form.getProductId(), form.getQuantity());
        List<CartItemOption> selectedOptions = validateOptions(
                form.getProductId(),
                form.getOptionIds());
        String requirements = normalizeRequirements(form.getRequirements());
        long cartId = getOrCreateLockedCart(memberId);

        List<CartItem> existingItems = cartMapper.findItemsByMemberId(memberId);
        Map<Long, List<CartItemOption>> optionsByItem = optionsByItem(existingItems);

        for (CartItem item : existingItems) {
            if (item.getProductId().equals(form.getProductId())
                    && java.util.Objects.equals(item.getRequirements(), requirements)
                    && sameOptionSnapshots(
                    optionsByItem.getOrDefault(item.getId(), List.of()),
                    selectedOptions)) {
                int mergedQuantity = item.getQuantity() + form.getQuantity();
                validateStock(product, mergedQuantity);
                updateQuantity(memberId, item.getId(), mergedQuantity);
                return;
            }
        }

        CartItem item = new CartItem();
        item.setCartId(cartId);
        item.setProductId(form.getProductId());
        item.setQuantity(form.getQuantity());
        item.setRequirements(requirements);
        if (cartMapper.insertItem(item) != 1) {
            throw new BusinessException(CartErrorCode.UPDATE_FAILED);
        }

        for (CartItemOption option : selectedOptions) {
            option.setCartItemId(item.getId());
            if (cartMapper.insertItemOption(option) != 1) {
                throw new BusinessException(CartErrorCode.UPDATE_FAILED);
            }
        }
    }

    @Transactional
    public void updateQuantity(long memberId, long itemId, int quantity) {
        cartMapper.findCartIdByMemberIdForUpdate(memberId)
                .orElseThrow(() -> new BusinessException(CartErrorCode.ITEM_NOT_FOUND));
        CartItem item = findOwnedItem(memberId, itemId);
        ProductSalesInfo product = getAvailableProduct(item.getProductId(), quantity);
        validateStock(product, quantity);
        if (cartMapper.updateItemQuantity(memberId, itemId, quantity) != 1) {
            throw new BusinessException(CartErrorCode.UPDATE_FAILED);
        }
    }

    @Transactional
    public void deleteItem(long memberId, long itemId) {
        deleteSelectedItems(memberId, List.of(itemId));
    }

    @Transactional
    public void deleteSelectedItems(long memberId, List<Long> itemIds) {
        List<Long> distinctIds = itemIds == null
                ? List.of()
                : itemIds.stream().filter(java.util.Objects::nonNull).distinct().toList();
        if (distinctIds.isEmpty()) {
            throw new BusinessException(CartErrorCode.ITEM_NOT_FOUND);
        }

        cartMapper.deleteOptionsByMemberIdAndItemIds(memberId, distinctIds);
        cartMapper.deleteImagesByMemberIdAndItemIds(memberId, distinctIds);
        if (cartMapper.deleteItemsByMemberIdAndItemIds(memberId, distinctIds)
                != distinctIds.size()) {
            throw new BusinessException(CartErrorCode.ITEM_NOT_FOUND);
        }
    }

    @Transactional
    public void clearCart(long memberId) {
        cartMapper.deleteAllOptionsByMemberId(memberId);
        cartMapper.deleteAllImagesByMemberId(memberId);
        cartMapper.deleteAllItemsByMemberId(memberId);
    }

    private long getOrCreateLockedCart(long memberId) {
        cartMapper.insertCartIfAbsent(memberId);
        return cartMapper.findCartIdByMemberIdForUpdate(memberId)
                .orElseThrow(() -> new BusinessException(CartErrorCode.UPDATE_FAILED));
    }

    private CartItem findOwnedItem(long memberId, long itemId) {
        return cartMapper.findItemByMemberIdAndItemId(memberId, itemId)
                .orElseThrow(() -> new BusinessException(CartErrorCode.ITEM_NOT_FOUND));
    }

    private ProductSalesInfo getAvailableProduct(long productId, int quantity) {
        ProductSalesInfo product;
        try {
            product = productQueryService.getSalesInfo(productId);
        } catch (BusinessException exception) {
            throw new BusinessException(CartErrorCode.PRODUCT_NOT_ON_SALE);
        }
        validateStock(product, quantity);
        return product;
    }

    private void validateStock(ProductSalesInfo product, int quantity) {
        if (quantity < 1
                || !product.available()
                || product.stockQuantity() != null && quantity > product.stockQuantity()) {
            throw new BusinessException(CartErrorCode.OUT_OF_STOCK);
        }
    }

    private List<CartItemOption> validateOptions(long productId, List<Long> requestedIds) {
        List<Long> normalizedIds = requestedIds == null
                ? List.of()
                : requestedIds.stream().filter(java.util.Objects::nonNull).distinct().toList();
        if (requestedIds != null && normalizedIds.size() != requestedIds.size()) {
            throw new BusinessException(CartErrorCode.INVALID_OPTION);
        }

        List<ProductOptionGroupView> groups = productService.getPublicOptionGroups(productId);
        Set<Long> remaining = new HashSet<>(normalizedIds);
        List<CartItemOption> selected = new ArrayList<>();

        for (ProductOptionGroupView group : groups) {
            List<ProductOptionItemView> groupSelections = group.options().stream()
                    .filter(option -> remaining.remove(option.id()))
                    .toList();
            if (group.required() && groupSelections.isEmpty()) {
                throw new BusinessException(CartErrorCode.REQUIRED_OPTION_MISSING);
            }
            if (!"MULTIPLE".equals(group.selectionType()) && groupSelections.size() > 1) {
                throw new BusinessException(CartErrorCode.INVALID_OPTION_SELECTION);
            }
            for (ProductOptionItemView optionView : groupSelections) {
                CartItemOption option = new CartItemOption();
                option.setProductOptionId(optionView.id());
                option.setOptionName(optionView.name());
                option.setAdditionalPrice(optionView.additionalPrice());
                selected.add(option);
            }
        }
        if (!remaining.isEmpty()) {
            throw new BusinessException(CartErrorCode.INVALID_OPTION);
        }
        return selected;
    }

    private Map<Long, List<CartItemOption>> optionsByItem(List<CartItem> items) {
        if (items.isEmpty()) {
            return Map.of();
        }
        List<Long> itemIds = items.stream().map(CartItem::getId).toList();
        Map<Long, List<CartItemOption>> result = new HashMap<>();
        for (CartItemOption option : cartMapper.findOptionsByCartItemIds(itemIds)) {
            result.computeIfAbsent(option.getCartItemId(), ignored -> new ArrayList<>())
                    .add(option);
        }
        return result;
    }

    private Set<Long> optionIds(List<CartItemOption> options) {
        return options.stream()
                .map(CartItemOption::getProductOptionId)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }

    private boolean sameOptionSnapshots(
            List<CartItemOption> existingOptions,
            List<CartItemOption> selectedOptions
    ) {
        if (!optionIds(existingOptions).equals(optionIds(selectedOptions))) {
            return false;
        }

        Map<Long, CartItemOption> selectedById = selectedOptions.stream()
                .collect(java.util.stream.Collectors.toMap(
                        CartItemOption::getProductOptionId,
                        option -> option));
        return existingOptions.stream().allMatch(existing -> {
            CartItemOption selected = selectedById.get(existing.getProductOptionId());
            return selected != null
                    && java.util.Objects.equals(existing.getOptionName(), selected.getOptionName())
                    && existing.getAdditionalPrice().compareTo(selected.getAdditionalPrice()) == 0;
        });
    }

    private CartItemView toView(CartItem item, List<CartItemOption> options) {
        ProductSalesInfo product;
        boolean available = true;
        try {
            product = productQueryService.getSalesInfo(item.getProductId());
            available = product.available()
                    && (product.stockQuantity() == null
                    || item.getQuantity() <= product.stockQuantity());
        } catch (BusinessException exception) {
            product = new ProductSalesInfo(
                    item.getProductId(),
                    "판매 중지 상품",
                    ProductType.GENERAL,
                    0,
                    false,
                    BigDecimal.ZERO,
                    0);
            available = false;
        }

        List<CartOptionView> optionViews = options.stream()
                .map(option -> new CartOptionView(
                        option.getProductOptionId(),
                        option.getOptionName(),
                        option.getAdditionalPrice()))
                .toList();
        BigDecimal optionPrice = optionViews.stream()
                .map(CartOptionView::additionalPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal unitPrice = product.basePrice().add(optionPrice);

        return new CartItemView(
                item.getId(),
                item.getProductId(),
                product.productName(),
                product.productType(),
                item.getQuantity(),
                product.stockQuantity(),
                available,
                product.basePrice(),
                optionPrice,
                unitPrice.multiply(BigDecimal.valueOf(item.getQuantity())),
                item.getRequirements(),
                optionViews);
    }

    private CartView emptyCart() {
        return new CartView(
                List.of(),
                0,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO);
    }

    private String normalizeRequirements(String requirements) {
        return requirements == null || requirements.isBlank()
                ? null
                : requirements.trim();
    }
}
