package com.cakeshop.domain.order.dto.form;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OrderCancelForm {

    @NotBlank
    @Size(max = 200)
    private String reason;
}
