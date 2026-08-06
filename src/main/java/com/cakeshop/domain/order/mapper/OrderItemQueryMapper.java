package com.cakeshop.domain.order.mapper;

import com.cakeshop.domain.order.dto.view.OrderItemDetail;
import com.cakeshop.domain.order.dto.view.PickedUpOrderItem;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * ******************************
 * 작성자 : HyunGyu-Cho
 * 담당자 : 주환
 * 작성일 : 2026-08-06
 * 기능 : 후기 작성 자격 조회 계약
 * 설명 : OrderItemQueryService 가 쓰는 조회 전용 매퍼다. 조각 1(#109).
 * ******************************
 *
 * <p>기존 {@code OrderMapper} 에 문장을 더하지 않고 매퍼를 새로 둔 것은 주문 담당자의 파일을
 * 건드리지 않기 위해서다. 한 도메인에 매퍼를 여럿 두는 선례는 community·member·payment 에 있다.
 */
@Mapper
public interface OrderItemQueryMapper {

    /**
     * 픽업 완료된 주문 상품을 제외 목록을 빼고 한 페이지 돌려준다.
     *
     * <p>제외 목록을 계약이 받는 이유는 호출측이 뒤에서 거르면 페이지가 어긋나기 때문이다.
     * 최근 20건이 전부 제외 대상이면 첫 페이지가 통째로 비고 건수도 틀어진다.
     */
    List<PickedUpOrderItem> findPickedUpItems(
            @Param("memberId") long memberId,
            @Param("excludedOrderItemIds") List<Long> excludedOrderItemIds,
            @Param("size") int size,
            @Param("offset") int offset
    );

    /** {@link #findPickedUpItems} 와 같은 조건의 전체 건수. */
    long countPickedUpItems(
            @Param("memberId") long memberId,
            @Param("excludedOrderItemIds") List<Long> excludedOrderItemIds
    );

    /** 주문 상품 단건. 주문 상태와 무관하게 돌려준다. 없으면 {@code null}. */
    OrderItemDetail findOrderItem(@Param("orderItemId") long orderItemId);
}
