package com.cakeshop.domain.product.dto.form;

import com.cakeshop.domain.product.entity.ProductOptionSelectionType;
import com.cakeshop.domain.product.entity.ProductOptionStatus;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** 관리자 옵션 그룹 등록·수정 입력값. */
@Getter
@Setter
public class ProductOptionGroupForm {

    @NotBlank(message = "옵션 그룹 이름을 입력해 주세요.")
    @Size(
            max = 100,
            message = "옵션 그룹 이름은 100자 이하로 입력해 주세요."
    )
    private String name;

    private boolean required;

    @NotNull(message = "선택 방식을 선택해 주세요.")
    private ProductOptionSelectionType selectionType;

    @NotNull(message = "상태를 선택해 주세요.")
    private ProductOptionStatus status =
            ProductOptionStatus.ACTIVE;

    public String normalizedName() {
        return name == null
                ? null
                : name.trim();
    }
}
