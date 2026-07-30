package com.cakeshop.domain.member.dto.form;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class PasswordRecoveryFormTests {

    private final Validator validator =
            Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void validate_validMemberInfo_acceptsForm() {
        PasswordRecoveryForm form = validForm();

        assertThat(validator.validate(form)).isEmpty();
    }

    @Test
    void validate_invalidMemberInfo_rejectsEachField() {
        PasswordRecoveryForm form = new PasswordRecoveryForm();
        form.setEmail("invalid-email");
        form.setName(" ");
        form.setBirthDate(LocalDate.now().plusDays(1));
        form.setPhone("1234");

        assertThat(validator.validate(form))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("email", "name", "birthDate", "phone");
    }

    private PasswordRecoveryForm validForm() {
        PasswordRecoveryForm form = new PasswordRecoveryForm();
        form.setEmail("member@example.com");
        form.setName("홍길동");
        form.setBirthDate(LocalDate.of(2000, 1, 15));
        form.setPhone("010-1234-5678");
        return form;
    }
}
