package com.cakeshop.domain.order.service.checkout;

import com.cakeshop.domain.store.dto.view.StoreView;
import com.cakeshop.domain.store.service.StoreService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;

/** 매장 운영 정보로 일반 주문의 픽업 가능 시각을 판단한다. */
@Component
@RequiredArgsConstructor
public class PickupAvailabilityPolicy {

    private final StoreService storeService;

    public boolean isAvailable(LocalDateTime pickupAt) {
        if (pickupAt == null) {
            return false;
        }

        StoreView store = storeService.getStoreView();
        DayOfWeek dayOfWeek = pickupAt.getDayOfWeek();
        if (store.closedDays().contains(dayOfWeek)
                || store.holidays().stream()
                        .anyMatch(holiday -> holiday.getHolidayDate().equals(pickupAt.toLocalDate()))) {
            return false;
        }

        boolean weekend = dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY;
        LocalTime businessStart = weekend ? store.weekendOpenTime() : store.weekdayOpenTime();
        LocalTime businessEnd = weekend ? store.weekendCloseTime() : store.weekdayCloseTime();
        LocalTime pickupTime = pickupAt.toLocalTime();

        if (!isWithin(pickupTime, businessStart, businessEnd)
                || !isWithin(pickupTime, store.pickupStartTime(), store.pickupEndTime())
                || store.pickupIntervalMinutes() == null
                || store.pickupIntervalMinutes() <= 0
                || pickupTime.getSecond() != 0
                || pickupTime.getNano() != 0) {
            return false;
        }

        long minutesFromStart = Duration.between(store.pickupStartTime(), pickupTime).toMinutes();
        return minutesFromStart % store.pickupIntervalMinutes() == 0;
    }

    private boolean isWithin(LocalTime value, LocalTime start, LocalTime end) {
        return value != null
                && start != null
                && end != null
                && !value.isBefore(start)
                && !value.isAfter(end);
    }
}
