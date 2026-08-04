package com.cakeshop.domain.member.dto.form;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

class WithdrawFormTests {

    private final Validator validator =
            Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void validate_passwordAndConfirmationProvided_acceptsForm() {
        WithdrawForm form = new WithdrawForm();
        form.setCurrentPassword("CurrentPassword1!");
        form.setWithdrawalConfirmed(true);

        assertThat(validator.validate(form)).isEmpty();
    }

    @Test
    void validate_missingPasswordAndConfirmation_rejectsForm() {
        WithdrawForm form = new WithdrawForm();

        assertThat(validator.validate(form))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("currentPassword", "withdrawalConfirmed");
    }
}
