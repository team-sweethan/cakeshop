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
     *
     * <p>이미 쓴 후기가 가리키는 주문 상품의 상품명·주문번호를 묶음으로 돌려준다.</p>
     *
     * <p>{@link #findWritableOrderItems} 로는 대신할 수 없다 — 그쪽은 <b>미작성</b> 항목만
     * 돌려주므로 이미 쓴 후기의 상품명을 받을 자리가 없다
     * ({@code docs/review/specs/review-read.md} B3).</p>
     *
     * <p><b>회원으로 좁히지 않는다.</b> 관리자 후기 목록도 같은 조회를 쓰는데 그쪽은 남의 후기를
     * 보는 것이 정상이라, 소유권 판정은 후기를 고르는 자리에 둔다. 호출하는 쪽은 <b>이미 권한을
     * 확인한 식별자만</b> 넘겨야 한다.</p>
     *
     * <p>없는 식별자는 결과에서 빠질 뿐 예외로 다루지 않는다. 후기 한 건 때문에 목록 전체가
     * 보이지 않게 되면 안 되기 때문이다.</p>
     */
    @Transactional(readOnly = true)
    public List<OrderReviewSnapshotView> findOrderItemSnapshots(Collection<Long> orderItemIds) {
        if (orderItemIds == null || orderItemIds.isEmpty()) {
            return List.of();
        }
        return orderReviewMapper.findSnapshotsByOrderItemIds(orderItemIds);
    }
}
