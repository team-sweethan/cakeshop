package com.cakeshop.domain.order.dto.form.customer;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public abstract class CreateOrderForm {

    @NotBlank
    @Pattern(regexp = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}"
            + "-[89aAbB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$")
    private String requestKey;

    @NotBlank
    @Size(max = 50)
    private String ordererName;

    @NotBlank
    @Size(max = 30)
    private String ordererPhone;

    @NotBlank
    @Size(max = 50)
    private String pickupName;

    @NotBlank
    @Size(max = 30)
    private String pickupPhone;

    @NotNull
    @Future
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime pickupAt;

    @Size(max = 1000)
    private String requestMessage;

    /** 쿠폰 도메인이 발급한 회원 쿠폰 식별자다. 주문 생성 시 서버가 소유권·사용 가능 여부를 다시 검증한다. */
    @Positive
    private Long memberCouponId;

    /** 주문서에 표시한 서버 기준 원금이다. 주문 생성 시 최신 금액과 비교해 화면 갱신 여부만 판단한다. */
    @NotNull
    @Positive
    private BigDecimal displayedOriginalAmount;
}
