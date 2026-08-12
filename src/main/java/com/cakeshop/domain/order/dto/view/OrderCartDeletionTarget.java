package com.cakeshop.domain.order.dto.view;

import java.util.List;

/** 결제 완료 후 cart 도메인이 주문에서 선택된 장바구니 항목을 삭제하는 데 필요한 최소 정보다. */
public record OrderCartDeletionTarget(long memberId, List<Long> cartItemIds) {
}
