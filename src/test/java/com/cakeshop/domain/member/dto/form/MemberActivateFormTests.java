package com.cakeshop.domain.member.dto.form;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

class MemberActivateFormTests {

    private final Validator validator =
            Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void validate_reasonWithOuterSpaces_normalizesBeforeLengthValidation() {
        MemberActivateForm form = new MemberActivateForm();

        form.setReason("  " + "가".repeat(500) + "  ");

        assertThat(validator.validate(form)).isEmpty();
        assertThat(form.getReason()).hasSize(500);
    }

    @Test
    void validate_blankReason_rejectsForm() {
        MemberActivateForm form = new MemberActivateForm();

        form.setReason("   ");

        assertThat(validator.validate(form))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("reason");
    }
}
