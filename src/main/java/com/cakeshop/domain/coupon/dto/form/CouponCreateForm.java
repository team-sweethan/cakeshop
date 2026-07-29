package com.cakeshop.domain.coupon.dto.form;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.cakeshop.domain.coupon.entity.DiscountType;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

/**
 * 관리자 쿠폰 등록 화면의 입력값과 Bean Validation 규칙을 담는다.
 * 상태·발급 수량·생성자처럼 서버가 결정하는 값은 포함하지 않는다.
 */
@Getter
@Setter
public class CouponCreateForm {

    @NotBlank(message = "쿠폰명을 입력해 주세요.")
    @Size(max = 100, message = "쿠폰명은 100자 이하여야 합니다.")
    private String name;

    @NotNull(message = "할인 유형을 선택해 주세요.")
    private DiscountType discountType;

    @NotNull(message = "할인값을 입력해 주세요.")
    @DecimalMin(value = "0.01", message = "할인값은 0보다 커야 합니다.")
    @Digits(
            integer = 10,
            fraction = 2,
            message = "할인값은 정수 10자리, 소수점 2자리 이하여야 합니다."
    )
    private BigDecimal discountValue;

    @NotNull(message = "최소 주문 금액을 입력해 주세요.")
    @DecimalMin(value = "0", message = "최소 주문 금액은 0 이상이어야 합니다.")
    @Digits(
            integer = 12,
            fraction = 0,
            message = "최소 주문 금액은 12자리 이하의 정수여야 합니다."
    )
    private BigDecimal minimumOrderAmount = BigDecimal.ZERO;

    @DecimalMin(value = "0", message = "최대 할인 금액은 0 이상이어야 합니다.")
    @Digits(
            integer = 12,
            fraction = 0,
            message = "최대 할인 금액은 12자리 이하의 정수여야 합니다."
    )
    private BigDecimal maximumDiscountAmount;

    @NotNull(message = "총 발급 수량을 입력해 주세요.")
    @Positive(message = "총 발급 수량은 1개 이상이어야 합니다.")
    private Long totalQuantity;

    @NotNull(message = "사용 시작 일시를 입력해 주세요.")
    @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm")
    private LocalDateTime startsAt;

    @NotNull(message = "사용 종료 일시를 입력해 주세요.")
    @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm")
    private LocalDateTime expiresAt;

    @AssertTrue(message = "종료 일시는 시작 일시보다 뒤여야 합니다.")
    public boolean isPeriodValid() {
        return startsAt == null || expiresAt == null || startsAt.isBefore(expiresAt);
    }

    @AssertTrue(message = "비율 할인은 최대 할인 금액을 입력해야 합니다.")
    public boolean isMaxDiscountAmountValid() {
        if (discountType == DiscountType.PERCENTAGE) {
            return maximumDiscountAmount != null;
        }
        return true;
    }

    @AssertTrue(message = "비율 할인의 할인값은 100 이하여야 합니다.")
    public boolean isDiscountValueValid() {
        if (discountType == DiscountType.PERCENTAGE) {
            return discountValue == null || discountValue.compareTo(BigDecimal.valueOf(100)) <= 0;
        }
        return true;
    }

}
