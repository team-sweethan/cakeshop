package com.cakeshop.domain.order.dto.form;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

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

    @Size(max = 20)
    private List<@NotNull @Positive Long> optionIds = new ArrayList<>();
}
