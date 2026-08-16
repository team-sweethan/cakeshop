package com.cakeshop.domain.cart.dto.form;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

class CartAddFormTests {

    private final Validator validator =
            Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void validate_requirementsWithOuterSpaces_normalizesBeforeLengthValidation() {
        CartAddForm form = validForm();
        form.setRequirements("  " + "가".repeat(2_000) + "  ");

        assertThat(validator.validate(form)).isEmpty();
        assertThat(form.getRequirements()).hasSize(2_000);
    }

    @Test
    void validate_requirementsLongerThanLimitAfterNormalization_rejectsForm() {
        CartAddForm form = validForm();
        form.setRequirements(" " + "가".repeat(2_001) + " ");

        assertThat(validator.validate(form))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("requirements");
    }

    private CartAddForm validForm() {
        CartAddForm form = new CartAddForm();
        form.setProductId(1L);
        form.setQuantity(1);
        return form;
    }
}
