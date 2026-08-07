package com.cakeshop.domain.coupon.dto.form;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

class CouponFormValidationTests {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void validateCreateForm_totalQuantityExceedsIntegerRange_rejectsValue() {
        CouponCreateForm form = new CouponCreateForm();
        form.setTotalQuantity((long) Integer.MAX_VALUE + 1);

        assertThat(validator.validateProperty(form, "totalQuantity"))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("totalQuantity");
    }

    @Test
    void validateUpdateForm_totalQuantityExceedsIntegerRange_rejectsValue() {
        CouponUpdateForm form = new CouponUpdateForm();
        form.setTotalQuantity((long) Integer.MAX_VALUE + 1);

        assertThat(validator.validateProperty(form, "totalQuantity"))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("totalQuantity");
    }
}
