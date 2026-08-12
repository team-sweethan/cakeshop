package com.cakeshop.domain.order.service;

import com.cakeshop.domain.order.dto.view.OrderCartDeletionTarget;
import com.cakeshop.domain.order.dto.view.OrderCartItemLink;
import com.cakeshop.domain.order.mapper.OrderCartMapper;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 작성자: 정후 / order 담당자: 주환
 * cart 도메인이 결제 완료 주문의 장바구니 정리 대상을 확인하도록 제공하는 공개 Query 계약이다.
 */
@Service
@RequiredArgsConstructor
public class OrderCartQueryService {

    private final OrderCartMapper orderCartMapper;

    @Transactional(readOnly = true)
    public Optional<OrderCartDeletionTarget> findCartDeletionTarget(long orderId) {
        var rows = orderCartMapper.findCartDeletionTargets(orderId);
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new OrderCartDeletionTarget(
                rows.getFirst().memberId(),
                rows.stream()
                        .map(row -> new OrderCartItemLink(row.cartItemId(), row.snapshotQuantity()))
                        .toList()
        ));
    }
}
