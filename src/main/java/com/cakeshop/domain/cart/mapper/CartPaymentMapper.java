package com.cakeshop.domain.cart.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 결제 완료 후 선택 장바구니 항목을 정리하는 cart 소유 Mapper다. */
@Mapper
public interface CartPaymentMapper {

    int deleteOptionsByMemberIdAndItemIds(@Param("memberId") long memberId, @Param("itemIds") List<Long> itemIds);

    int deleteImagesByMemberIdAndItemIds(@Param("memberId") long memberId, @Param("itemIds") List<Long> itemIds);

    int deleteItemsByMemberIdAndItemIds(@Param("memberId") long memberId, @Param("itemIds") List<Long> itemIds);
}
