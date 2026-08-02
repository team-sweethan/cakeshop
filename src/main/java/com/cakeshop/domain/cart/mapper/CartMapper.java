package com.cakeshop.domain.cart.mapper;

import com.cakeshop.domain.cart.entity.CartItem;
import com.cakeshop.domain.cart.entity.CartItemOption;
import java.util.List;
import java.util.Optional;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface CartMapper {

    Optional<Long> findCartIdByMemberId(@Param("memberId") long memberId);

    int insertCartIfAbsent(@Param("memberId") long memberId);

    Optional<Long> findCartIdByMemberIdForUpdate(@Param("memberId") long memberId);

    List<CartItem> findItemsByMemberId(@Param("memberId") long memberId);

    int sumQuantityByMemberId(@Param("memberId") long memberId);

    Optional<CartItem> findItemByMemberIdAndItemId(
            @Param("memberId") long memberId,
            @Param("itemId") long itemId
    );

    List<CartItemOption> findOptionsByCartItemIds(
            @Param("itemIds") List<Long> itemIds
    );

    int insertItem(CartItem item);

    int insertItemOption(CartItemOption option);

    int updateItemQuantity(
            @Param("memberId") long memberId,
            @Param("itemId") long itemId,
            @Param("quantity") int quantity
    );

    int deleteOptionsByMemberIdAndItemIds(
            @Param("memberId") long memberId,
            @Param("itemIds") List<Long> itemIds
    );

    int deleteImagesByMemberIdAndItemIds(
            @Param("memberId") long memberId,
            @Param("itemIds") List<Long> itemIds
    );

    int deleteItemsByMemberIdAndItemIds(
            @Param("memberId") long memberId,
            @Param("itemIds") List<Long> itemIds
    );

    int deleteAllOptionsByMemberId(@Param("memberId") long memberId);

    int deleteAllImagesByMemberId(@Param("memberId") long memberId);

    int deleteAllItemsByMemberId(@Param("memberId") long memberId);
}
