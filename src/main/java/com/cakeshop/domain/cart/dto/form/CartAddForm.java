package com.cakeshop.domain.cart.dto.form;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.ArrayList;
import java.util.List;

public class CartAddForm {

    @NotNull
    @Positive
    private Long productId;

    @Positive
    private int quantity = 1;

    private List<Long> optionIds = new ArrayList<>();

    @Size(max = 2000)
    private String requirements;

    public Long getProductId() {
        return productId;
    }

    public void setProductId(Long productId) {
        this.productId = productId;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public List<Long> getOptionIds() {
        return optionIds == null ? List.of() : optionIds;
    }

    public void setOptionIds(List<Long> optionIds) {
        this.optionIds = optionIds;
    }

    public String getRequirements() {
        return requirements;
    }

    public void setRequirements(String requirements) {
        this.requirements = requirements;
    }
}
