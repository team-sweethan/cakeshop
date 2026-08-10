package com.cakeshop.domain.order.service;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cakeshop.domain.order.dto.view.OrderReviewItemView;
import com.cakeshop.domain.order.dto.view.OrderReviewSnapshotView;
import com.cakeshop.domain.order.dto.view.OrderReviewTargetView;
import com.cakeshop.domain.order.mapper.OrderReviewMapper;
import com.cakeshop.global.common.paging.PageRequest;
import com.cakeshop.global.common.paging.PageResult;

/**
 * ******************************
 * 작성자 : HyunGyu-Cho
 * 담당자 : 주환
 * 작성일 : 2026-08-08
 * 기능 : 후기 작성이 쓰는 주문 상품 조회 계약
 * 설명 : 후기 작성 대상 목록과 자격 검증용 단건을 제공한다. 계약의 근거는 docs/review/specs/review-write.md A1.
 * ******************************
 */
@Service
public class OrderReviewQueryService {

    private final OrderReviewMapper orderReviewMapper;

    public OrderReviewQueryService(OrderReviewMapper orderReviewMapper) {
        this.orderReviewMapper = orderReviewMapper;
    }

    /**
     * 후기를 아직 쓰지 않은 픽업 완료 주문 상품을 페이지 단위로 돌려준다.
     *
     * <p>제외할 식별자를 받아 <b>페이징과 함께</b> 처리한다. 호출한 쪽이 뒤에서 거르면 건수와 페이지
     * 경계가 어긋나는데, 그 경로는 {@code specs/review-write.md} A1 에 적혀 있다.</p>
     */
    @Transactional(readOnly = true)
    public PageResult<OrderReviewItemView> findWritableOrderItems(
            long memberId, Collection<Long> excludedOrderItemIds, PageRequest pageRequest) {

        Collection<Long> excluded =
                excludedOrderItemIds == null ? List.of() : excludedOrderItemIds;

        long total = orderReviewMapper.countWritableOrderItems(memberId, excluded);
        if (total <= pageRequest.getOffset()) {
            return new PageResult<>(List.of(), pageRequest, total);
        }

        List<OrderReviewItemView> content =
                orderReviewMapper.findWritableOrderItems(
                        memberId, excluded, pageRequest.getOffset(), pageRequest.getSize());

        return new PageResult<>(content, pageRequest, total);
    }

    /**
     * 후기 작성 자격 검증에 쓰는 주문 상품 단건.
     *
     * <p><b>없는 것과 남의 것을 구분해 주지 않는다</b> — 둘 다 404 다({@code DOMAIN.md} 2.5).
     * 반면 <b>픽업 전은 비우지 않고 돌려준다</b> — 400 이라 응답이 달라, 여기서 함께 걸러 내면
     * 호출한 쪽이 둘을 가릴 수 없다.</p>
     */
    @Transactional(readOnly = true)
    public Optional<OrderReviewTargetView> findReviewTarget(long orderItemId, long memberId) {
        return Optional.ofNullable(orderReviewMapper.findReviewTarget(orderItemId, memberId));
    }

    /**
     * ******************************
     * 작성자 : HyunGyu-Cho
     * 담당자 : 주환
     * 작성일 : 2026-08-10
     * 기능 : 이미 쓴 후기의 주문 상품 스냅샷 묶음 조회
     * 설명 : 후기 목록(B3·C1·C3)이 order_items·orders 를 직접 JOIN 하지 않도록 추가한다.
     *        계약의 근거는 docs/review/DOMAIN.md 2.7.
     * ******************************
     */
    @Transactional(readOnly = true)
    public List<OrderReviewSnapshotView> findOrderItemSnapshots(Collection<Long> orderItemIds) {
        if (orderItemIds == null || orderItemIds.isEmpty()) {
            return List.of();
        }
        return orderReviewMapper.findSnapshotsByOrderItemIds(orderItemIds);
    }

    /**
     * ******************************
     * 작성자 : HyunGyu-Cho
     * 담당자 : 주환
     * 작성일 : 2026-08-10
     * 기능 : 관리자 후기 검색의 상품명 조건
     * 설명 : 상품명이 부분 일치하는 주문 상품 ID 를 돌려준다. 빈 목록은 "조건에 맞는 주문 상품이
     *        없다"는 뜻이므로, 조건을 걸지 않는 경우는 호출한 쪽이 이 메서드를 부르지 않는 것으로
     *        가른다. 계약의 근거는 docs/review/specs/review-admin.md C2.
     * ******************************
     */
    @Transactional(readOnly = true)
    public List<Long> findOrderItemIdsByProductName(String keyword) {
        String normalized = keyword == null ? "" : keyword.trim();

        if (normalized.isEmpty()) {
            return List.of();
        }

        return orderReviewMapper.findOrderItemIdsByProductName(escapeLikeKeyword(normalized));
    }

    // '!' 를 먼저 바꾸지 않으면 뒤에서 만들어 낸 '!%' 를 다시 이스케이프해 패턴이 어긋난다.
    private static String escapeLikeKeyword(String keyword) {
        return keyword.replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }
}
