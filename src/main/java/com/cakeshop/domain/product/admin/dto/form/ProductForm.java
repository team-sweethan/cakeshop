package com.cakeshop.domain.product.admin.dto.form;

import java.math.BigDecimal;

import com.cakeshop.domain.product.entity.ProductType;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import lombok.Getter;
import lombok.Setter;

/**
 * 관리자 상품 등록 및 수정 입력값을 담는다.
 */
@Getter
@Setter
public class ProductForm {

    /** 상품이 소속될 카테고리 식별자. */
    @NotNull(message = "카테고리를 선택해 주세요.")
    private Long categoryId;

    /** 상품명. */
    @NotBlank(message = "상품명을 입력해 주세요.")
    @Size(
            max = 150,
            message = "상품명은 150자 이하여야 합니다."
    )
    private String name;

    /** 상품 설명. */
    @Size(
            max = 5_000,
            message = "상품 설명은 5,000자 이하여야 합니다."
    )
    private String description;

    /** 옵션 가격을 제외한 기본 판매 가격. */
    @NotNull(message = "판매 가격을 입력해 주세요.")
    @DecimalMin(
            value = "0",
            message = "판매 가격은 0원 이상이어야 합니다."
    )
    @Digits(
            integer = 12,
            fraction = 0,
            message = "판매 가격은 12자리 이하의 정수여야 합니다."
    )
    private BigDecimal basePrice;

    /**
     * 현재 재고 수량.
     *
     * <p>{@code null}이면 재고 제한이 없고,
     * 0이면 품절, 1 이상이면 주문 가능한 수량을 의미한다.</p>
     */
    @Min(
            value = 0,
            message = "재고 수량은 0개 이상이어야 합니다."
    )
    private Integer stockQuantity;

    /** 일반 케이크 또는 주문 제작 상품 유형. */
    @NotNull(message = "상품 유형을 선택해 주세요.")
    private ProductType productType;

    /** 주문부터 상품 준비 완료까지 필요한 일수. */
    @NotNull(message = "상품 준비 일수를 입력해 주세요.")
    @Min(
            value = 0,
            message = "상품 준비 일수는 0일 이상이어야 합니다."
    )
    @Max(
            value = 65_535,
            message = "상품 준비 일수가 너무 큽니다."
    )
    private Integer preparationDays = 0;

    /** 취소할 수 있는 픽업일 이전 제한 일수. */
    @NotNull(message = "취소 제한 일수를 입력해 주세요.")
    @Min(
            value = 0,
            message = "취소 제한 일수는 0일 이상이어야 합니다."
    )
    @Max(
            value = 65_535,
            message = "취소 제한 일수가 너무 큽니다."
    )
    private Integer cancellationLimitDays = 0;

    /**
     * 앞뒤 공백을 제거한 상품명을 반환한다.
     *
     * @return 정리된 상품명
     */
    public String normalizedName() {
        return name == null
                ? null
                : name.trim();
    }

    /**
     * 앞뒤 공백을 제거한 상품 설명을 반환한다.
     *
     * @return 정리된 상품 설명 또는 설명이 없으면 {@code null}
     */
    public String normalizedDescription() {
        if (description == null || description.isBlank()) {
            return null;
        }

        return description.trim();
    }
}