package com.cakeshop.domain.cart.service;

import com.cakeshop.domain.cart.dto.view.CartOrderItemView;
import com.cakeshop.domain.cart.dto.view.CartOrderItemRow;
import com.cakeshop.domain.cart.entity.CartItemOption;
import com.cakeshop.domain.cart.error.CartErrorCode;
import com.cakeshop.domain.cart.mapper.CartOrderMapper;
import com.cakeshop.global.error.BusinessException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 작성자: 주환 / cart 담당자: 수민
 * 주문 도메인이 로그인 회원 소유의 선택 장바구니 항목을 조회하도록 제공하는 공개 Query 계약이다.
 */
@Service
@RequiredArgsConstructor
public class CartOrderQueryService {

    private final CartOrderMapper cartOrderMapper;

    @Transactional(readOnly = true)
    public List<CartOrderItemView> getSelectedOrderItems(long memberId, List<Long> itemIds) {
        List<Long> normalizedIds = itemIds == null ? List.of() : itemIds.stream()
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        if (normalizedIds.isEmpty()) {
            throw new BusinessException(CartErrorCode.ITEM_NOT_FOUND);
        }

        List<CartOrderItemRow> items = cartOrderMapper.findSelectedOrderItems(memberId, normalizedIds);
        if (items.size() != normalizedIds.size()) {
            throw new BusinessException(CartErrorCode.ITEM_NOT_FOUND);
        }

        Map<Long, List<Long>> optionIdsByItem = cartOrderMapper.findOptionsByCartItemIds(normalizedIds)
                .stream()
                .collect(Collectors.groupingBy(
                        CartItemOption::getCartItemId,
                        Collectors.mapping(CartItemOption::getProductOptionId, Collectors.toList())
                ));
        return items.stream()
                .map(item -> new CartOrderItemView(
                        item.cartItemId(), item.productId(), item.quantity(), item.requirements(),
                        optionIdsByItem.getOrDefault(item.cartItemId(), List.of())
                ))
                .toList();
    }
}
