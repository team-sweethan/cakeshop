package com.cakeshop.domain.order.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;

class OrderTypeTests {

    @Test
    void providesOrderTypeLabelsInOnePlace() {
        assertThat(Map.of(
                OrderType.GENERAL, "일반 상품",
                OrderType.CUSTOM, "주문 제작"
        )).allSatisfy((type, label) -> assertThat(type.orderTypeLabel()).isEqualTo(label));
    }
}
