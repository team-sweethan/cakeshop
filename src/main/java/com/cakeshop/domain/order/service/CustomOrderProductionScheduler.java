package com.cakeshop.domain.order.service;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 수제 주문 제작 완료 예정 시각을 1분 주기로 확인한다. */
@Component
@RequiredArgsConstructor
public class CustomOrderProductionScheduler {

    private final CustomOrderProductionCompletionService productionCompletionService;

    @Scheduled(
            initialDelayString = "${app.order.production-completion.initial-delay-ms:60000}",
            fixedDelayString = "${app.order.production-completion.fixed-delay-ms:60000}"
    )
    public void completeDueOrders() {
        productionCompletionService.completeDueOrders();
    }
}
