package com.cakeshop.domain.order.service;

import com.cakeshop.domain.order.dto.view.OrderItemDetail;
import com.cakeshop.domain.order.dto.view.PickedUpOrderItem;
import com.cakeshop.domain.order.mapper.OrderItemQueryMapper;
import com.cakeshop.global.common.paging.PageRequest;
import com.cakeshop.global.common.paging.PageResult;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * ******************************
 * 작성자 : HyunGyu-Cho
 * 담당자 : 주환
 * 작성일 : 2026-08-06
 * 기능 : 후기 작성 자격 조회 계약
 * 설명 : 리뷰 도메인에 픽업 완료 주문 상품을 공개한다. 조각 1(#109).
 * ******************************
 *
 * <p>{@code docs/conventions.md} 15절이 다른 도메인의 테이블·Mapper 직접 사용을 금지하므로,
 * 리뷰가 {@code orders}·{@code order_items} 를 읽을 자리를 주문 도메인이 계약으로 연다.
 *
 * <p>주문이 리뷰를 아는 것은 아니다. 제외 목록은 <b>불투명한 ID 묶음</b>일 뿐이라 의존 방향이
 * 뒤집히지 않는다.
 */
@Service
public class OrderItemQueryService {

    private final OrderItemQueryMapper orderItemQueryMapper;

    public OrderItemQueryService(OrderItemQueryMapper orderItemQueryMapper) {
        this.orderItemQueryMapper = orderItemQueryMapper;
    }

    /**
     * 픽업 완료된 주문 상품을 제외 목록을 빼고 페이징해 돌려준다.
     *
     * <p><b>제외와 페이징을 계약이 함께 처리한다.</b> 호출측이 페이지를 받은 뒤에 거르면
     * 최근 20건이 전부 제외 대상일 때 첫 페이지가 통째로 비고, 그 항목들이 다음 페이지로
     * 밀리며 전체 건수도 틀어진다. 목록이 비는 것은 정상 상태라 오류로도 드러나지 않는다.
     *
     * @param memberId 조회할 회원 식별자
     * @param excludedOrderItemIds 제외할 주문 상품 식별자. {@code null} 이거나 비어도 된다
     * @param pageRequest 페이지 번호와 크기
     * @return 해당 페이지의 주문 상품과 전체 건수
     */
    @Transactional(readOnly = true)
    public PageResult<PickedUpOrderItem> findPickedUpItems(
            long memberId,
            Collection<Long> excludedOrderItemIds,
            PageRequest pageRequest
    ) {
        List<Long> excluded = toExcludedList(excludedOrderItemIds);

        long totalElements =
                orderItemQueryMapper.countPickedUpItems(memberId, excluded);
        if (totalElements == 0) {
            return new PageResult<>(List.of(), pageRequest, 0);
        }

        List<PickedUpOrderItem> content =
                orderItemQueryMapper.findPickedUpItems(
                        memberId,
                        excluded,
                        pageRequest.getSize(),
                        pageRequest.getOffset()
                );

        return new PageResult<>(content, pageRequest, totalElements);
    }

    /**
     * 주문 상품 단건을 돌려준다.
     *
     * <p><b>주문 상태로 걸러 내지 않는다.</b> 호출측은 "없음"과 "픽업 전"에 다른 응답을 주어야
     * 하는데, 계약이 미리 걸러 버리면 그 둘을 구분할 수 없다.
     *
     * @param orderItemId 주문 상품 식별자
     * @return 주문 상품 정보, 없으면 {@link Optional#empty()}
     */
    @Transactional(readOnly = true)
    public Optional<OrderItemDetail> findOrderItem(long orderItemId) {
        return Optional.ofNullable(orderItemQueryMapper.findOrderItem(orderItemId));
    }

    /** {@code null} 과 중복을 걷어 낸다. MyBatis 는 비어 있으면 조건을 붙이지 않는다. */
    private List<Long> toExcludedList(Collection<Long> excludedOrderItemIds) {
        if (excludedOrderItemIds == null || excludedOrderItemIds.isEmpty()) {
            return List.of();
        }
        return excludedOrderItemIds.stream()
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
    }
}
