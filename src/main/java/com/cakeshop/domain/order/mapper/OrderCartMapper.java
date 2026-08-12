package com.cakeshop.domain.order.mapper;

import com.cakeshop.domain.order.dto.view.OrderCartDeletionTarget;
import com.cakeshop.domain.order.dto.view.OrderCartDeletionRow;
import java.util.List;
import java.util.Optional;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 장바구니 선택 주문과 결제 완료 후 정리 사이의 연결 정보를 관리한다. */
@Mapper
public interface OrderCartMapper {

    int insertOrderCartItems(@Param("orderId") long orderId, @Param("cartItemIds") List<Long> cartItemIds);

    Optional<OrderCartDeletionRow> findCartDeletionTarget(@Param("orderId") long orderId);
}
