package com.cakeshop.domain.order.service;

import com.cakeshop.domain.order.mapper.OrderMapper;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 준비 기간이 끝난 수제 주문을 조건부 갱신으로 픽업 준비 상태로 완료한다. */
@Service
@RequiredArgsConstructor
public class CustomOrderProductionCompletionService {

    private final OrderMapper orderMapper;
    private final Clock clock;

    @Value("${app.order.production-completion.batch-size:100}")
    private int batchSize;

    /** 재실행 가능한 한 배치의 자동 제작 완료 작업이다. */
    @Transactional
    public int completeDueOrders() {
        LocalDateTime now = LocalDateTime.now(clock);
        int safeBatchSize = Math.max(1, batchSize);
        return orderMapper.findDueCustomProductionOrderIds(now, safeBatchSize)
                .stream()
                .mapToInt(orderId -> orderMapper.markReadyForPickupIfInProduction(orderId, now))
                .sum();
    }
}
