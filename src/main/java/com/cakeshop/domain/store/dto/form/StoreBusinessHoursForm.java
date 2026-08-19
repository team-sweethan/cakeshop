package com.cakeshop.domain.store.dto.form;

import com.cakeshop.domain.store.dto.view.StoreView;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.HashSet;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;

/** 관리자 매장 영업시간 입력 전용 DTO다. */
@Getter
@Setter
public class StoreBusinessHoursForm {

    @NotNull(message = "평일 영업 시작 시간을 입력해 주세요.")
    private LocalTime weekdayOpenTime;

    @NotNull(message = "평일 영업 종료 시간을 입력해 주세요.")
    private LocalTime weekdayCloseTime;

    @NotNull(message = "주말 영업 시작 시간을 입력해 주세요.")
    private LocalTime weekendOpenTime;

    @NotNull(message = "주말 영업 종료 시간을 입력해 주세요.")
    private LocalTime weekendCloseTime;

    private Set<DayOfWeek> closedDays = new HashSet<>();

    public static StoreBusinessHoursForm from(StoreView store) {
        StoreBusinessHoursForm form = new StoreBusinessHoursForm();
        form.setWeekdayOpenTime(store.weekdayOpenTime());
        form.setWeekdayCloseTime(store.weekdayCloseTime());
        form.setWeekendOpenTime(store.weekendOpenTime());
        form.setWeekendCloseTime(store.weekendCloseTime());
        form.setClosedDays(new HashSet<>(store.closedDays()));
        return form;
    }

    @AssertTrue(message = "평일 영업 종료 시간은 시작 시간보다 늦어야 합니다.")
    public boolean isWeekdayTimeRangeValid() {
        return isIncreasing(weekdayOpenTime, weekdayCloseTime);
    }

    @AssertTrue(message = "주말 영업 종료 시간은 시작 시간보다 늦어야 합니다.")
    public boolean isWeekendTimeRangeValid() {
        return isIncreasing(weekendOpenTime, weekendCloseTime);
    }

    private boolean isIncreasing(LocalTime start, LocalTime end) {
        return start == null || end == null || start.isBefore(end);
    }

    // null 유입 시 빈 컬렉션으로 방어하는 커스텀 로직이라 Lombok @Setter 대신 직접 정의한다.
    public void setClosedDays(Set<DayOfWeek> closedDays) {
        this.closedDays = closedDays == null ? new HashSet<>() : closedDays;
    }
}
