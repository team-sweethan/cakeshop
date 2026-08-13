package com.cakeshop.domain.cart.mapper;

import com.cakeshop.domain.cart.dto.view.CartOrderItemView;
import com.cakeshop.domain.cart.dto.view.CartOrderItemRow;
import com.cakeshop.domain.cart.entity.CartItemOption;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 주문 도메인 연동에 필요한 장바구니 선택 항목만 조회한다. */
@Mapper
public interface CartOrderMapper {

    List<CartOrderItemRow> findSelectedOrderItems(
            @Param("memberId") long memberId,
            @Param("itemIds") List<Long> itemIds
    );

    List<CartItemOption> findOptionsByCartItemIds(@Param("itemIds") List<Long> itemIds);
}
