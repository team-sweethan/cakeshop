package com.cakeshop.domain.member.dto.form;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

class MemberSuspendFormTests {

    private final Validator validator =
            Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void validate_reasonWith500Characters_acceptsForm() {
        MemberSuspendForm form = new MemberSuspendForm();

        form.setReason("가".repeat(500));

        assertThat(validator.validate(form)).isEmpty();
    }

    @Test
    void validate_blankReason_rejectsForm() {
        MemberSuspendForm form = new MemberSuspendForm();

        form.setReason("   ");

        assertThat(validator.validate(form))
                .extracting(violation ->
                        violation.getPropertyPath().toString())
                .contains("reason");
    }

    @Test
    void validate_missingReason_rejectsForm() {
        MemberSuspendForm form = new MemberSuspendForm();

        assertThat(validator.validate(form))
                .extracting(violation ->
                        violation.getPropertyPath().toString())
                .contains("reason");
    }

    @Test
    void validate_reasonWith501Characters_rejectsForm() {
        MemberSuspendForm form = new MemberSuspendForm();

        form.setReason("가".repeat(501));

        assertThat(validator.validate(form))
                .extracting(violation ->
                        violation.getPropertyPath().toString())
                .contains("reason");
    }
}
