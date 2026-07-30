package com.cakeshop.domain.member.dto.form;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class EmailRecoveryFormTests {

    private final Validator validator =
            Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void validate_validMemberInfo_hasNoViolations() {
        EmailRecoveryForm form = validForm();

        assertThat(validator.validate(form)).isEmpty();
    }

    @Test
    void validate_missingMemberInfo_rejectsRequiredFields() {
        EmailRecoveryForm form = new EmailRecoveryForm();

        assertThat(validator.validate(form))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("name", "birthDate", "phone");
    }

    @Test
    void validate_invalidPhoneAndFutureBirthDate_rejectsForm() {
        EmailRecoveryForm form = validForm();
        form.setPhone("1234");
        form.setBirthDate(LocalDate.now().plusDays(1));

        assertThat(validator.validate(form))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("phone", "birthDate");
    }

    private EmailRecoveryForm validForm() {
        EmailRecoveryForm form = new EmailRecoveryForm();
        form.setName("홍길동");
        form.setBirthDate(LocalDate.of(2000, 1, 15));
        form.setPhone("010-1234-5678");
        return form;
    }
}
