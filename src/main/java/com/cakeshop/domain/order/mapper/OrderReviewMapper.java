package com.cakeshop.domain.order.mapper;

import java.util.Collection;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.cakeshop.domain.order.dto.view.OrderReviewItemView;
import com.cakeshop.domain.order.dto.view.OrderReviewTargetView;

/**
 * ******************************
 * 작성자 : HyunGyu-Cho
 * 담당자 : 주환
 * 작성일 : 2026-08-08
 * 기능 : 후기 작성이 쓰는 주문 상품 조회 SQL 계약
 * 설명 : 리뷰 Mapper 가 order_items·orders 를 직접 참조하지 않도록 주문 도메인에 분리해 둔다.
 * ******************************
 *
 * <p>{@code OrderMapper} 에 얹지 않고 파일을 새로 둔다 — 담당자의 기존 파일을 건드리지 않으려는
 * 것이고, {@code OrderCouponQueryMapper} 가 같은 이유로 이미 갈라져 있다.</p>
 */
@Mapper
public interface OrderReviewMapper {

    /**
     * 후기를 아직 쓰지 않은 픽업 완료 주문 상품을 페이지 단위로 조회한다.
     *
     * <p>제외 목록을 <b>SQL 안에서</b> 적용한다. 페이지를 먼저 자르고 호출한 쪽이 뒤에서 거르면
     * 잘린 20건이 전부 작성 완료일 때 화면이 통째로 빈다.</p>
     */
    List<OrderReviewItemView> findWritableOrderItems(
            @Param("memberId") long memberId,
            @Param("excludedOrderItemIds") Collection<Long> excludedOrderItemIds,
            @Param("offset") int offset,
            @Param("size") int size);

    /** 위 목록의 전체 건수. 같은 조건을 쓰지 않으면 화면의 건수와 실제 건수가 갈린다. */
    long countWritableOrderItems(
            @Param("memberId") long memberId,
            @Param("excludedOrderItemIds") Collection<Long> excludedOrderItemIds);

    /**
     * 후기 작성 자격 검증용 단건 조회. <b>소유 회원이 아니면 {@code null} 이다.</b>
     *
     * <p>픽업 여부와 무관하게 돌려준다 — 픽업 전(400)과 없음·남의 것(404)은 다른 응답이라
     * 여기서 함께 걸러 내면 호출한 쪽이 둘을 구분할 수 없다.</p>
     */
    OrderReviewTargetView findReviewTarget(
            @Param("orderItemId") long orderItemId, @Param("memberId") long memberId);
}
