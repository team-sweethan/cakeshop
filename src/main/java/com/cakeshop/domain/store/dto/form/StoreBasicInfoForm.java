package com.cakeshop.domain.store.dto.form;

import com.cakeshop.domain.store.dto.view.StoreView;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** 관리자 매장 기본정보 입력 전용 DTO다. */
@Getter
@Setter
public class StoreBasicInfoForm {

    @NotBlank(message = "매장명을 입력해 주세요.")
    @Size(max = 100, message = "매장명은 100자 이하여야 합니다.")
    private String name;

    @Size(max = 1000, message = "매장 소개는 1,000자 이하여야 합니다.")
    private String description;

    @NotBlank(message = "주소를 입력해 주세요.")
    @Size(max = 255, message = "주소는 255자 이하여야 합니다.")
    private String address;

    @NotBlank(message = "연락처를 입력해 주세요.")
    @Pattern(regexp = "^[0-9+() -]{8,30}$", message = "연락처 형식을 확인해 주세요.")
    private String phone;

    public static StoreBasicInfoForm from(StoreView store) {
        StoreBasicInfoForm form = new StoreBasicInfoForm();
        form.setName(store.name());
        form.setDescription(store.description());
        form.setAddress(store.address());
        form.setPhone(store.phone());
        return form;
    }
}
