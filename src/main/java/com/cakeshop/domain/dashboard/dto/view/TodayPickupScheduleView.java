package com.cakeshop.domain.dashboard.dto.view;

import java.time.LocalDateTime;

/** 관리자 대시보드의 오늘 픽업 일정 한 건을 전달한다. */
public record TodayPickupScheduleView(
        long orderId,
        LocalDateTime pickupAt,
        String orderNumber,
        String productName,
        PickupUrgency urgency
) {
}
