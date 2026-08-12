package com.cakeshop.domain.order.dto.view;

/** OrderCartMapper의 GROUP_CONCAT 결과를 공개 DTO로 변환하기 전 사용하는 내부 행이다. */
public record OrderCartDeletionRow(long memberId, String cartItemIds) {
}
