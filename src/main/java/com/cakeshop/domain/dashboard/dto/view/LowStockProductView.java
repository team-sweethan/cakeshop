package com.cakeshop.domain.dashboard.dto.view;

/** 관리자 대시보드의 재고 부족 상품 한 건을 전달한다. */
public record LowStockProductView(
        long productId,
        String productName,
        int stockQuantity
) {
}
