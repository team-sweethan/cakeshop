package com.cakeshop.domain.order.mapper;

import com.cakeshop.domain.order.dto.view.OrderCartDeletionRow;
import com.cakeshop.domain.order.dto.view.OrderCartItemLink;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 장바구니 선택 주문과 결제 후 정리에 필요한 주문-장바구니 연결 정보를 관리한다. */
@Mapper
public interface OrderCartMapper {

    int insertOrderCartItems(@Param("orderId") long orderId, @Param("items") List<OrderCartItemLink> items);

    List<OrderCartDeletionRow> findCartDeletionTargets(@Param("orderId") long orderId);
}
