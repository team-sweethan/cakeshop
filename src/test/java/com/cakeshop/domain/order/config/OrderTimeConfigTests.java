package com.cakeshop.domain.order.config;

import java.time.Clock;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OrderTimeConfigTests {

    @Test
    void orderClock_usesAsiaSeoulRegardlessOfServerTimezone() {
        Clock clock = new OrderTimeConfig().orderClock();

        assertThat(clock.getZone()).isEqualTo(ZoneId.of("Asia/Seoul"));
    }
}
