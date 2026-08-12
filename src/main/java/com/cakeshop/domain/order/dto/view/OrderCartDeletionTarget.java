package com.cakeshop.domain.order.dto.view;

import java.util.List;

/** 결제 완료 후 장바구니 정리에 필요한 주문 소유 연결 정보다. */
public record OrderCartDeletionTarget(long memberId, List<OrderCartItemLink> items) {
}
