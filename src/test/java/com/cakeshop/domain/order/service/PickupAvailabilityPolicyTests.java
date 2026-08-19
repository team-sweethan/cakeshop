package com.cakeshop.domain.order.service;

import com.cakeshop.domain.order.service.checkout.PickupAvailabilityPolicy;
import com.cakeshop.domain.store.dto.view.StoreView;
import com.cakeshop.domain.store.entity.StoreHoliday;
import com.cakeshop.domain.store.service.StoreService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PickupAvailabilityPolicyTests {

    private static final LocalDateTime PICKUP_AT = LocalDateTime.of(2026, 8, 3, 11, 0);

    @Mock
    private StoreService storeService;

    private PickupAvailabilityPolicy pickupAvailabilityPolicy;

    @BeforeEach
    void setUp() {
        pickupAvailabilityPolicy = new PickupAvailabilityPolicy(storeService);
    }

    @Test
    void isAvailable_openPickupInterval_returnsTrue() {
        when(storeService.getStoreView()).thenReturn(storeView(Set.of(), List.of(), 60));

        assertThat(pickupAvailabilityPolicy.isAvailable(PICKUP_AT)).isTrue();
    }

    @Test
    void isAvailable_closedDay_returnsFalse() {
        when(storeService.getStoreView()).thenReturn(storeView(
                Set.of(PICKUP_AT.getDayOfWeek()),
                List.of(),
                60
        ));

        assertThat(pickupAvailabilityPolicy.isAvailable(PICKUP_AT)).isFalse();
    }

    @Test
    void isAvailable_storeHoliday_returnsFalse() {
        StoreHoliday holiday = new StoreHoliday();
        holiday.setHolidayDate(PICKUP_AT.toLocalDate());
        when(storeService.getStoreView()).thenReturn(storeView(Set.of(), List.of(holiday), 60));

        assertThat(pickupAvailabilityPolicy.isAvailable(PICKUP_AT)).isFalse();
    }

    @Test
    void isAvailable_outsidePickupHours_returnsFalse() {
        when(storeService.getStoreView()).thenReturn(storeView(Set.of(), List.of(), 60));

        assertThat(pickupAvailabilityPolicy.isAvailable(PICKUP_AT.withHour(9))).isFalse();
    }

    @Test
    void isAvailable_offInterval_returnsFalse() {
        when(storeService.getStoreView()).thenReturn(storeView(Set.of(), List.of(), 60));

        assertThat(pickupAvailabilityPolicy.isAvailable(PICKUP_AT.plusMinutes(30))).isFalse();
    }

    @Test
    void isAvailable_withSeconds_returnsFalse() {
        when(storeService.getStoreView()).thenReturn(storeView(Set.of(), List.of(), 60));

        assertThat(pickupAvailabilityPolicy.isAvailable(PICKUP_AT.plusSeconds(1))).isFalse();
    }

    private StoreView storeView(
            Set<DayOfWeek> closedDays,
            List<StoreHoliday> holidays,
            int pickupIntervalMinutes
    ) {
        return new StoreView(
                1L,
                "테스트 매장",
                null,
                null,
                "서울시",
                "02-0000-0000",
                LocalTime.of(9, 0),
                LocalTime.of(20, 0),
                LocalTime.of(9, 0),
                LocalTime.of(20, 0),
                closedDays,
                "1층",
                LocalTime.of(10, 0),
                LocalTime.of(19, 0),
                pickupIntervalMinutes,
                holidays
        );
    }
}
