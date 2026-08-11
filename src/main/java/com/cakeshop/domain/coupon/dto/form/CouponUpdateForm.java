package com.cakeshop.domain.coupon.dto.form;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.springframework.format.annotation.DateTimeFormat;

import com.cakeshop.domain.coupon.entity.DiscountType;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 관리자 쿠폰 수정 요청의 입력값과 검증 규칙이다.
 * 발급 대상은 등록 이후 변경할 수 없으므로 요청 Form에 포함하지 않는다.
 */
@Getter
@Setter
public class CouponUpdateForm {

    @NotBlank(message = "쿠폰명을 입력해 주세요.")
    @Size(max = 100, message = "쿠폰명은 100자 이하여야 합니다.")
    private String name;

    @NotNull(message = "할인 유형을 선택해 주세요.")
    private DiscountType discountType;

    @NotNull(message = "할인값을 입력해 주세요.")
    @DecimalMin(value = "0.01", message = "할인값은 0보다 커야 합니다.")
    @Digits(integer = 10, fraction = 2, message = "할인값 형식이 올바르지 않습니다.")
    private BigDecimal discountValue;

    @NotNull(message = "최소 주문 금액을 입력해 주세요.")
    @DecimalMin(value = "0", message = "최소 주문 금액은 0 이상이어야 합니다.")
    @Digits(integer = 12, fraction = 0, message = "최소 주문 금액 형식이 올바르지 않습니다.")
    private BigDecimal minimumOrderAmount = BigDecimal.ZERO;

    @DecimalMin(value = "0", message = "최대 할인 금액은 0 이상이어야 합니다.")
    @Digits(integer = 12, fraction = 0, message = "최대 할인 금액 형식이 올바르지 않습니다.")
    private BigDecimal maximumDiscountAmount;

    @Positive(message = "총 발급 수량은 1개 이상이어야 합니다.")
    @Max(value = Integer.MAX_VALUE, message = "총 발급 수량은 2,147,483,647 이하여야 합니다.")
    private Long totalQuantity;

    @NotNull(message = "사용 시작 일시를 입력해 주세요.")
    @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm")
    private LocalDateTime startsAt;

    @NotNull(message = "사용 종료 일시를 입력해 주세요.")
    @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm")
    private LocalDateTime expiresAt;

    @AssertTrue(message = "종료 일시는 시작 일시보다 늦어야 합니다.")
    public boolean isPeriodValid() {
        return startsAt == null || expiresAt == null || startsAt.isBefore(expiresAt);
    }

    @AssertTrue(message = "비율 할인은 최대 할인 금액을 입력해야 합니다.")
    public boolean isMaxDiscountAmountValid() {
        return discountType != DiscountType.PERCENTAGE || maximumDiscountAmount != null;
    }

    @AssertTrue(message = "비율 할인값은 100 이하여야 합니다.")
    public boolean isDiscountValueValid() {
        return discountType != DiscountType.PERCENTAGE
                || discountValue == null
                || discountValue.compareTo(BigDecimal.valueOf(100)) <= 0;
    }

}
