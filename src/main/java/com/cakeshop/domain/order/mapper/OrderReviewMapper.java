package com.cakeshop.domain.order.mapper;

import java.util.Collection;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.cakeshop.domain.order.dto.view.OrderReviewItemView;
import com.cakeshop.domain.order.dto.view.OrderReviewSnapshotView;
import com.cakeshop.domain.order.dto.view.OrderReviewTargetView;

/**
 * ******************************
 * 작성자 : HyunGyu-Cho
 * 담당자 : 주환
 * 작성일 : 2026-08-08
 * 기능 : 후기 작성이 쓰는 주문 상품 조회 SQL 계약
 * 설명 : 리뷰 Mapper 가 order_items·orders 를 직접 참조하지 않도록 주문 도메인에 분리해 둔다.
 *        담당자의 기존 파일을 건드리지 않으려고 OrderMapper 에 얹지 않고 새로 뒀다.
 * ******************************
 */
@Mapper
public interface OrderReviewMapper {

    List<OrderReviewItemView> findWritableOrderItems(
            @Param("memberId") long memberId,
            @Param("excludedOrderItemIds") Collection<Long> excludedOrderItemIds,
            @Param("offset") int offset,
            @Param("size") int size);

    long countWritableOrderItems(
            @Param("memberId") long memberId,
            @Param("excludedOrderItemIds") Collection<Long> excludedOrderItemIds);

    /** 소유 회원이 아니면 {@code null}. 픽업 여부와 무관하게 돌려준다. */
    OrderReviewTargetView findReviewTarget(
            @Param("orderItemId") long orderItemId, @Param("memberId") long memberId);

    /** 주문 상태를 거르지 않는다. 이미 쓴 후기가 가리키는 항목이라 자격은 작성 시점에 끝났다. */
    List<OrderReviewSnapshotView> findSnapshotsByOrderItemIds(
            @Param("orderItemIds") Collection<Long> orderItemIds);
}
