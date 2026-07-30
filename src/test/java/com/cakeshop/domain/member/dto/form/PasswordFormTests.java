package com.cakeshop.domain.member.dto.form;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

class PasswordFormTests {

    private final Validator validator =
            Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void validate_validPassword_acceptsForm() {
        PasswordForm form = new PasswordForm();
        form.setNewPassword("NewPassword1!");
        form.setNewPasswordConfirm("NewPassword1!");

        assertThat(validator.validate(form)).isEmpty();
    }

    @Test
    void validate_weakAndMismatchedPassword_rejectsForm() {
        PasswordForm form = new PasswordForm();
        form.setNewPassword("password");
        form.setNewPasswordConfirm("different");

        assertThat(validator.validate(form))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("newPassword", "passwordConfirmed");
    }
}
