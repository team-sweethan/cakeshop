package com.cakeshop.domain.order.service;

import com.cakeshop.domain.order.service.payment.OrderExpirationService;
import com.cakeshop.domain.order.service.payment.OrderExpireScheduler;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class OrderExpireSchedulerTests {

    @Test
    void expireOverdueOrders_delegatesToExpirationService() {
        OrderExpirationService service = mock(OrderExpirationService.class);
        OrderExpireScheduler scheduler = new OrderExpireScheduler(service);

        scheduler.expireOverdueOrders();

        verify(service).expireOverdueOrders();
    }
}
