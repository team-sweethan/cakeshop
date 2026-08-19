package com.cakeshop.domain.order.mapper;

import com.cakeshop.domain.order.dto.view.OrderChatView;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;

/**
 * ******************************
 * 작성자 : 김민정
 * 담당자 : 주환
 * 작성일 : 2026-08-18
 * 기능 : 픽업 안내 알림용 주문 조회 SQL 계약
 * 설명 : 픽업 하루 전(내일), 픽업 당일(오늘) 및 픽업 완료(PICKED_UP) 상태 주문을 조회한다.
 * ******************************
 */
@Mapper
public interface OrderPickupNotificationMapper {

    /** 픽업 하루 전(내일) 예정 주문 목록 조회 */
    List<OrderChatView> findOrdersForPickupTomorrow();

    /** 픽업 당일(오늘) 예정 주문 목록 조회 */
    List<OrderChatView> findOrdersForPickupToday();
}
