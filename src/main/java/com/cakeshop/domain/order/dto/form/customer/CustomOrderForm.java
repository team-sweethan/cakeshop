package com.cakeshop.domain.order.dto.form.customer;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/** 수제 케이크 주문 생성에 필요한 고객 입력이다. 금액은 요청으로 받지 않는다. */
@Getter
@Setter
public class CustomOrderForm extends CreateOrderForm {

    @NotBlank
    @Pattern(regexp = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}"
            + "-[89aAbB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$")
    private String requestKey;

    @NotNull
    @Positive
    private Long productId;

    @Size(max = 20)
    private List<@NotNull @Positive Long> optionIds = new ArrayList<>();

    /** 쿠폰 도메인이 발급한 회원 쿠폰 식별자만 전달한다. */
    @Positive
    private Long memberCouponId;

    /** 주문 항목 스냅샷에 보관할 레터링 문구다. */
    @Size(max = 100)
    private String lettering;
}
