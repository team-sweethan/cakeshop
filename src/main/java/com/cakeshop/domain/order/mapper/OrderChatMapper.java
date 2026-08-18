package com.cakeshop.domain.order.mapper;

import com.cakeshop.domain.order.dto.view.OrderChatView;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * ******************************
 * 작성자 : 김민정
 * 담당자 : 주환
 * 작성일 : 2026-08-11
 * 기능 : 채팅 및 알림용 주문 정보 조회 SQL 계약
 * 설명 : orders 테이블 및 notifications 테이블에서 연동용 주문 상세 정보 및 알림 중복 여부를 조회한다.
 * ******************************
 */
@Mapper
public interface OrderChatMapper {
    OrderChatView findOrderById(@Param("orderId") Long orderId);
    List<OrderChatView> findOrdersByCustomerId(@Param("customerId") Long customerId);
    List<OrderChatView> findRecentStatusChangedOrders();
    int countNotificationByEventKey(@Param("eventKey") String eventKey);
}
