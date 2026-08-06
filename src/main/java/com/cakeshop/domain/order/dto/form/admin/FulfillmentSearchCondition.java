package com.cakeshop.domain.order.dto.form.admin;

import com.cakeshop.domain.order.entity.OrderStatus;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/** 관리자 제작·픽업 목록의 날짜와 주문 상태 검색 조건이다. */
@Getter
@Setter
public class FulfillmentSearchCondition {

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate pickupDate;

    private OrderStatus status;
}
