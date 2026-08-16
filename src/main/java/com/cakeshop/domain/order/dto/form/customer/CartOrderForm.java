package com.cakeshop.domain.order.dto.form.customer;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/** 장바구니에서 선택한 여러 일반 상품을 하나의 주문으로 생성하기 위한 입력 Form이다. */
@Getter
@Setter
public class CartOrderForm extends CreateOrderForm {

    /** 최초 주문서에서는 주문자 연락처를 픽업자 정보에 그대로 사용한다. */
    private boolean sameAsOrderer = true;

    @Size(min = 1)
    private List<@NotNull @Positive Long> cartItemIds = new ArrayList<>();
}
