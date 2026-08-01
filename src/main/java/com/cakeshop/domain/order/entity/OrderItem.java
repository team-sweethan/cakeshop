package com.cakeshop.domain.order.entity;

import com.cakeshop.domain.product.entity.ProductType;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/** DB의 order_items 한 행을 표현한다. */
@Getter
@Setter
public class OrderItem {

    private Long id;                    // 내부 주문 항목 식별자
    private Long orderId;               // 주문 식별자
    private Long productId;             // 주문한 원본 상품 식별자
    private String productName;         // 주문 당시 상품명 스냅샷
    private ProductType productType;    // 주문 당시 상품 유형 스냅샷
    private Integer quantity;           // 주문 수량
    private BigDecimal basePrice;       // 주문 당시 상품 1개당 기본 가격
    private BigDecimal optionAmount;    // 상품 1개당 선택 옵션 추가 금액 합계
    private BigDecimal totalAmount;     // (기본 가격 + 옵션 금액) × 주문 수량
    private String requirements;        // 주문 항목별 제작 요청 사항
    private Integer preparationDays;    // 주문 당시 상품 준비 기간 스냅샷
    private Integer cancellationLimitDays; // 주문 당시 취소 제한 일수
}
