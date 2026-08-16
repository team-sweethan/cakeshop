package com.cakeshop.domain.cart.service;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.cakeshop.domain.cart.mapper.CartMapper;
import com.cakeshop.domain.cart.mapper.CartPaymentMapper;
import com.cakeshop.domain.order.dto.view.OrderCartDeletionTarget;
import com.cakeshop.domain.order.dto.view.OrderCartItemLink;
import com.cakeshop.domain.order.service.OrderCartQueryService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@ExtendWith(MockitoExtension.class)
class CartPaymentCommandServiceTests {

    @Mock
    private CartPaymentMapper cartPaymentMapper;

    @Mock
    private CartMapper cartMapper;

    @Mock
    private OrderCartQueryService orderCartQueryService;

    private CartPaymentCommandService cartPaymentCommandService;

    @BeforeEach
    void setUp() {
        cartPaymentCommandService = new CartPaymentCommandService(
                cartPaymentMapper,
                cartMapper,
                orderCartQueryService
        );
    }

    @Test
    void removeItemsAfterPayment_deletesOnlyLinkedItemsOfOrderMember() {
        List<Long> cartItemIds = List.of(11L, 12L);
        when(orderCartQueryService.findCartDeletionTarget(100L)).thenReturn(
                Optional.of(new OrderCartDeletionTarget(10L, List.of(
                        new OrderCartItemLink(11L, 1),
                        new OrderCartItemLink(12L, 2)
                )))
        );
        when(cartPaymentMapper.findItemIdsMatchingSnapshotQuantity(
                org.mockito.ArgumentMatchers.eq(10L), org.mockito.ArgumentMatchers.anyList()
        )).thenReturn(cartItemIds);
        when(cartMapper.findCartIdByMemberIdForUpdate(10L)).thenReturn(Optional.of(20L));

        cartPaymentCommandService.removeItemsAfterPayment(100L);

        verify(cartPaymentMapper).deleteOptionsByMemberIdAndItemIds(10L, cartItemIds);
        verify(cartPaymentMapper).deleteImagesByMemberIdAndItemIds(10L, cartItemIds);
        verify(cartPaymentMapper).deleteItemsByMemberIdAndItemIds(10L, cartItemIds);
        InOrder cleanupOrder = inOrder(cartMapper, cartPaymentMapper);
        cleanupOrder.verify(cartMapper).findCartIdByMemberIdForUpdate(10L);
        cleanupOrder.verify(cartPaymentMapper).findItemIdsMatchingSnapshotQuantity(
                org.mockito.ArgumentMatchers.eq(10L), org.mockito.ArgumentMatchers.anyList());
    }

    @Test
    void removeItemsAfterPayment_keepsItemWhoseQuantityChangedAfterOrderCreation() {
        when(orderCartQueryService.findCartDeletionTarget(100L)).thenReturn(
                Optional.of(new OrderCartDeletionTarget(10L, List.of(new OrderCartItemLink(11L, 1))))
        );
        when(cartPaymentMapper.findItemIdsMatchingSnapshotQuantity(
                org.mockito.ArgumentMatchers.eq(10L), org.mockito.ArgumentMatchers.anyList()
        )).thenReturn(List.of());
        when(cartMapper.findCartIdByMemberIdForUpdate(10L)).thenReturn(Optional.of(20L));

        cartPaymentCommandService.removeItemsAfterPayment(100L);

        verify(cartPaymentMapper, never()).deleteItemsByMemberIdAndItemIds(
                org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyList()
        );
    }

    @Test
    void removeItemsAfterPayment_doesNothingWhenOrderHasNoCartLinks() {
        when(orderCartQueryService.findCartDeletionTarget(100L)).thenReturn(Optional.empty());

        cartPaymentCommandService.removeItemsAfterPayment(100L);

        verifyNoInteractions(cartPaymentMapper);
    }

    @Test
    void removeItemsAfterPayment_usesIndependentTransaction() throws NoSuchMethodException {
        Transactional transactional = CartPaymentCommandService.class
                .getMethod("removeItemsAfterPayment", long.class)
                .getAnnotation(Transactional.class);

        org.assertj.core.api.Assertions.assertThat(transactional).isNotNull();
        org.assertj.core.api.Assertions.assertThat(transactional.propagation())
                .isEqualTo(Propagation.REQUIRES_NEW);
    }
}
