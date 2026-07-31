package com.cakeshop.domain.order.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration(proxyBeanMethods = false)
public class OrderTimeConfig {

    @Bean
    public Clock orderClock() {
        return Clock.systemDefaultZone();
    }
}
