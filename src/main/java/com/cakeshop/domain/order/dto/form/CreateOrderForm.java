package com.cakeshop.domain.order.dto.form;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class CreateOrderForm {

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
    private LocalDateTime pickupAt;

    @Size(max = 1000)
    private String requestMessage;

    @Valid
    @NotEmpty
    private List<OrderItemForm> items = new ArrayList<>();
}
