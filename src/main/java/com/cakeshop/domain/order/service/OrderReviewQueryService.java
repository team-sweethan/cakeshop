package com.cakeshop.domain.order.service;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cakeshop.domain.order.dto.view.OrderReviewItemView;
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
 * 설명 : 후기 작성 대상 목록과 자격 검증용 단건을 제공한다. 조회만 하고 주문 상태를 바꾸지 않는다.
 * ******************************
 *
 * <p><b>{@code CommandService} 는 두지 않는다.</b> 리뷰가 주문에 하는 것은 조회뿐이고,
 * {@code conventions.md} 15.8 은 쓸 수도 있으니 미리 만드는 것을 금한다.</p>
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
     * <p><b>제외 목록과 페이징을 함께 책임진다.</b> 픽업 완료 주문 상품을 먼저 20건으로 자른 뒤
     * 호출한 쪽이 작성 완료를 걸러 내면, 최신 20건이 전부 작성 완료일 때 <b>첫 페이지가 통째로
     * 빈다.</b> 밀려난 항목은 다음 페이지로 가고 전체 건수도 틀어지는데, 목록이 비는 것은 정상
     * 상태라 오류로 드러나지도 않는다({@code specs/review-write.md} A1).</p>
     *
     * <p>제외할 식별자는 <b>불투명한 id 묶음</b>으로 받는다. 주문이 {@code reviews} 를 아는 것이
     * 아니므로 의존 방향이 뒤집히지 않는다.</p>
     *
     * <p>범위를 벗어난 페이지는 빈 목록으로 돌려준다. 요청 파라미터로 아무 페이지나 올 수 있고,
     * 그것은 오류가 아니다.</p>
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
     * 후기 작성 자격 검증에 쓰는 주문 상품 단건. <b>소유 회원이 아니면 비어서 돌아온다.</b>
     *
     * <p>없는 것과 남의 것을 구분해 주지 않는다 — 둘 다 404 이고, 남의 주문 상품 id 가 존재한다는
     * 사실을 알려 주지 않는 것이 그 판단의 이유다({@code DOMAIN.md} 2.5).</p>
     *
     * <p><b>픽업 전이어도 돌려준다.</b> 픽업 전은 400 이라 404 와 응답이 다르므로, 여기서 함께
     * 걸러 내면 호출한 쪽이 둘을 구분할 수 없다. 판단은 {@code pickedUp} 을 보고 리뷰가 한다.</p>
     */
    @Transactional(readOnly = true)
    public Optional<OrderReviewTargetView> findReviewTarget(long orderItemId, long memberId) {
        return Optional.ofNullable(orderReviewMapper.findReviewTarget(orderItemId, memberId));
    }
}
