package com.cakeshop.domain.order.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class OrderAmountCalculatorTests {

    @Test
    void calculate_multipliesProductAndOptionsByQuantity() {
        OrderAmountCalculator.OrderAmounts amounts = OrderAmountCalculator.calculate(
                BigDecimal.valueOf(35_000),
                2,
                List.of(new OrderOptionValidator.ValidatedOption(
                        10L, "크기", "2호", BigDecimal.valueOf(10_000)
                ))
        );

        assertThat(amounts.productAmount()).isEqualByComparingTo("70000");
        assertThat(amounts.optionAmount()).isEqualByComparingTo("20000");
        assertThat(amounts.totalAmount()).isEqualByComparingTo("90000");
        assertThat(amounts.unitOptionAmount()).isEqualByComparingTo("10000");
    }
}
