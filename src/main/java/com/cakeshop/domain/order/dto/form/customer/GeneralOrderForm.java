package com.cakeshop.domain.order.dto.form.customer;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class GeneralOrderForm extends CreateOrderForm {

    @NotBlank
    @Pattern(regexp = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}"
            + "-[89aAbB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$")
    private String requestKey;

    @NotNull
    @Positive
    private Long productId;

    @NotNull
    @Positive
    private Integer quantity;

    /** 쿠폰 도메인이 발급한 회원 쿠폰 식별자다. 주문 생성 시 서버가 소유권·사용 가능 여부를 다시 검증한다. */
    @Positive
    private Long memberCouponId;

    /** 주문서에 표시한 서버 기준 원금이다. 주문 생성 시 최신 금액과 비교해 화면 갱신 여부만 판단한다. */
    @NotNull
    @Positive
    private BigDecimal displayedOriginalAmount;

    @Size(max = 20)
    private List<@NotNull @Positive Long> optionIds = new ArrayList<>();
}
