package com.cakeshop.domain.cart.dto.form;

import jakarta.validation.constraints.NotEmpty;
import java.util.ArrayList;
import java.util.List;

public class CartDeleteSelectedForm {

    @NotEmpty
    private List<Long> itemIds = new ArrayList<>();

    public List<Long> getItemIds() {
        return itemIds;
    }

    public void setItemIds(List<Long> itemIds) {
        this.itemIds = itemIds;
    }
}
