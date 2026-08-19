package com.cakeshop.domain.store.dto.form;

import com.cakeshop.domain.store.dto.view.StoreView;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalTime;
import lombok.Getter;
import lombok.Setter;

/** 관리자 매장 픽업정보 입력 전용 DTO다. */
@Getter
@Setter
public class StorePickupInfoForm {

    @NotBlank(message = "픽업 장소를 입력해 주세요.")
    @Size(max = 255, message = "픽업 장소는 255자 이하여야 합니다.")
    private String pickupPlace;

    @NotNull(message = "픽업 시작 시간을 입력해 주세요.")
    private LocalTime pickupStartTime;

    @NotNull(message = "픽업 종료 시간을 입력해 주세요.")
    private LocalTime pickupEndTime;

    @NotNull(message = "픽업 시간 간격을 선택해 주세요.")
    @Min(value = 10, message = "픽업 간격은 10분 이상이어야 합니다.")
    @Max(value = 180, message = "픽업 간격은 180분 이하여야 합니다.")
    private Integer pickupIntervalMinutes;

    public static StorePickupInfoForm from(StoreView store) {
        StorePickupInfoForm form = new StorePickupInfoForm();
        form.setPickupPlace(store.pickupPlace());
        form.setPickupStartTime(store.pickupStartTime());
        form.setPickupEndTime(store.pickupEndTime());
        form.setPickupIntervalMinutes(store.pickupIntervalMinutes());
        return form;
    }

    @AssertTrue(message = "픽업 종료 시간은 시작 시간보다 늦어야 합니다.")
    public boolean isPickupTimeRangeValid() {
        return pickupStartTime == null
                || pickupEndTime == null
                || pickupStartTime.isBefore(pickupEndTime);
    }
}
