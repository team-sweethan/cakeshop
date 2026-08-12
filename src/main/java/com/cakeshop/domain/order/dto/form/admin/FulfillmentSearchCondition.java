package com.cakeshop.domain.order.dto.form.admin;

import com.cakeshop.domain.order.entity.OrderStatus;
import lombok.Getter;
import lombok.Setter;
/** 관리자 주문 처리 목록의 작업 단계 검색 조건이다. */
@Getter
@Setter
public class FulfillmentSearchCondition {
    private OrderStatus status;
}
