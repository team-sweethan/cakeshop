package com.cakeshop.domain.cart.service;

import com.cakeshop.domain.cart.dto.view.CartPaymentItemTarget;
import com.cakeshop.domain.cart.mapper.CartPaymentMapper;
import com.cakeshop.domain.order.service.OrderCartQueryService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 작성자: 정후 / cart 담당자: 수민, order 담당자: 주환
 * 결제 완료 주문에 연결된 선택 장바구니 항목을 별도 트랜잭션에서 멱등적으로 삭제하는 공개 Command 계약이다.
 */
@Service
@RequiredArgsConstructor
public class CartPaymentCommandService {

    private final CartPaymentMapper cartPaymentMapper;
    private final OrderCartQueryService orderCartQueryService;

    @Transactional
    public void removeItemsAfterPayment(long orderId) {
        orderCartQueryService.findCartDeletionTarget(orderId).ifPresent(target -> {
            List<Long> itemIds = cartPaymentMapper.findItemIdsMatchingSnapshotQuantity(
                    target.memberId(),
                    target.items().stream()
                            .map(item -> new CartPaymentItemTarget(item.cartItemId(), item.quantity()))
                            .toList()
            );
            if (itemIds.isEmpty()) {
                return;
            }
            // 이미 사용자가 삭제했거나 재처리한 경우에도 0건을 성공으로 취급한다.
            cartPaymentMapper.deleteOptionsByMemberIdAndItemIds(target.memberId(), itemIds);
            cartPaymentMapper.deleteImagesByMemberIdAndItemIds(target.memberId(), itemIds);
            cartPaymentMapper.deleteItemsByMemberIdAndItemIds(target.memberId(), itemIds);
        });
    }
}
