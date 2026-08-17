package com.cakeshop.domain.order.dto.form.customer;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class GeneralOrderForm extends CreateOrderForm {

    @NotNull
    @Positive
    private Long productId;

    @Size(max = 20)
    private List<@NotNull @Positive Long> optionIds = new ArrayList<>();

    @NotNull
    @Positive
    private Integer quantity;
}
