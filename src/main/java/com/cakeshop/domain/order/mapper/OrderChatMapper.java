package com.cakeshop.domain.order.mapper;

import com.cakeshop.domain.order.dto.view.OrderChatView;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * ******************************
 * 작성자 : 김민정
 * 담당자 : 주환
 * 작성일 : 2026-08-11
 * 기능 : 채팅 및 알림용 주문 정보 조회 SQL 계약
 * 설명 : orders 테이블에서 연동용 주문 상세 정보 및 상태 변경 최신 주문 목록을 조회한다.
 * ******************************
 */
@Mapper
public interface OrderChatMapper {
    OrderChatView findOrderById(@Param("orderId") Long orderId);
    List<OrderChatView> findOrdersByCustomerId(@Param("customerId") Long customerId);
    List<OrderChatView> findRecentStatusChangedOrders(@Param("since") LocalDateTime since);
}
