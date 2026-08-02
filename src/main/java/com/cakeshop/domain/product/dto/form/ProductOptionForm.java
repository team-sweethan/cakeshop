package com.cakeshop.domain.product.dto.form;

import java.math.BigDecimal;

import com.cakeshop.domain.product.entity.ProductOptionStatus;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** 관리자 개별 옵션 등록·수정 입력값. */
@Getter
@Setter
public class ProductOptionForm {

    @NotBlank(message = "옵션 이름을 입력해 주세요.")
    @Size(
            max = 100,
            message = "옵션 이름은 100자 이하로 입력해 주세요."
    )
    private String name;

    @NotNull(message = "추가 가격을 입력해 주세요.")
    @PositiveOrZero(message = "추가 가격은 0원 이상이어야 합니다.")
    @Digits(
            integer = 12,
            fraction = 0,
            message = "추가 가격은 12자리 이하의 정수여야 합니다."
    )
    private BigDecimal additionalPrice = BigDecimal.ZERO;

    @NotNull(message = "상태를 선택해 주세요.")
    private ProductOptionStatus status =
            ProductOptionStatus.ACTIVE;

    public String normalizedName() {
        return name == null
                ? null
                : name.trim();
    }
}
