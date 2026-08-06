package com.cakeshop.domain.order.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import org.junit.jupiter.api.Test;

class OrderStatusTests {

    @Test
    void definesOnlyDatabaseOrderStatuses() {
        assertThat(Set.of(OrderStatus.values()))
            .containsExactlyInAnyOrder(
                OrderStatus.PENDING_PAYMENT,
                OrderStatus.UNDER_REVIEW,
                OrderStatus.READY_FOR_PICKUP,
                OrderStatus.PICKED_UP,
                OrderStatus.CANCELED,
                OrderStatus.REJECTED,
                OrderStatus.EXPIRED
            );
    }

    @Test
    void allowsOnlyDefinedTransitions() {
        assertThat(OrderStatus.PENDING_PAYMENT.canTransitionTo(OrderStatus.UNDER_REVIEW)).isTrue();
        assertThat(OrderStatus.PENDING_PAYMENT.canTransitionTo(OrderStatus.READY_FOR_PICKUP)).isTrue();
        assertThat(OrderStatus.PENDING_PAYMENT.canTransitionTo(OrderStatus.EXPIRED)).isTrue();
        assertThat(OrderStatus.PENDING_PAYMENT.canTransitionTo(OrderStatus.CANCELED)).isTrue();

        assertThat(OrderStatus.UNDER_REVIEW.canTransitionTo(OrderStatus.READY_FOR_PICKUP)).isTrue();
        assertThat(OrderStatus.UNDER_REVIEW.canTransitionTo(OrderStatus.REJECTED)).isTrue();
        assertThat(OrderStatus.UNDER_REVIEW.canTransitionTo(OrderStatus.CANCELED)).isTrue();

        assertThat(OrderStatus.READY_FOR_PICKUP.canTransitionTo(OrderStatus.PICKED_UP)).isTrue();
        assertThat(OrderStatus.READY_FOR_PICKUP.canTransitionTo(OrderStatus.CANCELED)).isTrue();

        assertThat(OrderStatus.PENDING_PAYMENT.canTransitionTo(OrderStatus.PICKED_UP)).isFalse();
        assertThat(OrderStatus.UNDER_REVIEW.canTransitionTo(OrderStatus.PICKED_UP)).isFalse();
        assertThat(OrderStatus.READY_FOR_PICKUP.canTransitionTo(OrderStatus.REJECTED)).isFalse();
    }

    @Test
    void finalStatusesCannotTransition() {
        Set<OrderStatus> finalStatuses = Set.of(
            OrderStatus.PICKED_UP,
            OrderStatus.CANCELED,
            OrderStatus.REJECTED,
            OrderStatus.EXPIRED
        );

        assertThat(finalStatuses).allSatisfy(status -> {
            assertThat(status.isFinal()).isTrue();
            assertThat(Set.of(OrderStatus.values()))
                .noneMatch(status::canTransitionTo);
        });

        assertThat(Set.of(
            OrderStatus.PENDING_PAYMENT,
            OrderStatus.UNDER_REVIEW,
            OrderStatus.READY_FOR_PICKUP
        )).allSatisfy(status -> assertThat(status.isFinal()).isFalse());
    }
}
