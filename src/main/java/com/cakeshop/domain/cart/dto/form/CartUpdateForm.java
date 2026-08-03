package com.cakeshop.domain.cart.dto.form;

import jakarta.validation.constraints.Positive;

public class CartUpdateForm {

    @Positive
    private int quantity;

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }
}
