package com.cakeshop.domain.cart.service;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.cakeshop.domain.cart.mapper.CartPaymentMapper;
import com.cakeshop.domain.order.dto.view.OrderCartDeletionTarget;
import com.cakeshop.domain.order.dto.view.OrderCartItemLink;
import com.cakeshop.domain.order.service.OrderCartQueryService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CartPaymentCommandServiceTests {

    @Mock
    private CartPaymentMapper cartPaymentMapper;

    @Mock
    private OrderCartQueryService orderCartQueryService;

    private CartPaymentCommandService cartPaymentCommandService;

    @BeforeEach
    void setUp() {
        cartPaymentCommandService = new CartPaymentCommandService(
                cartPaymentMapper,
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

        cartPaymentCommandService.removeItemsAfterPayment(100L);

        verify(cartPaymentMapper).deleteOptionsByMemberIdAndItemIds(10L, cartItemIds);
        verify(cartPaymentMapper).deleteImagesByMemberIdAndItemIds(10L, cartItemIds);
        verify(cartPaymentMapper).deleteItemsByMemberIdAndItemIds(10L, cartItemIds);
    }

    @Test
    void removeItemsAfterPayment_keepsItemWhoseQuantityChangedAfterOrderCreation() {
        when(orderCartQueryService.findCartDeletionTarget(100L)).thenReturn(
                Optional.of(new OrderCartDeletionTarget(10L, List.of(new OrderCartItemLink(11L, 1))))
        );
        when(cartPaymentMapper.findItemIdsMatchingSnapshotQuantity(
                org.mockito.ArgumentMatchers.eq(10L), org.mockito.ArgumentMatchers.anyList()
        )).thenReturn(List.of());

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
}
