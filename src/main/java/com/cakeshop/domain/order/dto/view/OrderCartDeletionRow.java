package com.cakeshop.domain.order.dto.view;

/** 주문-장바구니 연결 테이블에서 결제 후 삭제 판단에 필요한 행을 전달한다. */
public record OrderCartDeletionRow(long memberId, long cartItemId, int snapshotQuantity) {
}
