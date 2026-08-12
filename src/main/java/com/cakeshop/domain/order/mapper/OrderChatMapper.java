package com.cakeshop.domain.order.mapper;

import com.cakeshop.domain.order.dto.view.OrderChatView;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * ******************************
 * 작성자 : 김민정
 * 담당자 : 주환
 * 작성일 : 2026-08-11
 * 기능 : 채팅용 주문 정보 조회 SQL 계약
 * 설명 : orders 테이블에서 채팅방 연동용 주문 상세 정보 DTO를 조회한다.
 * ******************************
 */
import java.util.List;

@Mapper
public interface OrderChatMapper {
    OrderChatView findOrderById(@Param("orderId") Long orderId);
    List<OrderChatView> findOrdersByCustomerId(@Param("customerId") Long customerId);
}
