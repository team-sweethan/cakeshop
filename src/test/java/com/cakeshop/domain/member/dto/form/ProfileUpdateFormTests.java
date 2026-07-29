package com.cakeshop.domain.member.dto.form;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ProfileUpdateFormTests {

    private final Validator validator =
            Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void validate_passwordFieldsBlank_acceptsBasicInfoUpdate() {
        ProfileUpdateForm form = validBasicInfo();

        assertThat(validator.validate(form)).isEmpty();
    }

    @Test
    void validate_passwordChangeIncomplete_rejectsForm() {
        ProfileUpdateForm form = validBasicInfo();
        form.setNewPassword("Password1!");

        Set<ConstraintViolation<ProfileUpdateForm>> violations = validator.validate(form);

        assertThat(violations)
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("passwordChangeComplete");
    }

    @Test
    void validate_nameLongerThanDatabaseLimit_rejectsForm() {
        ProfileUpdateForm form = validBasicInfo();
        form.setName("가".repeat(51));

        assertThat(validator.validate(form))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("name");
    }

    private ProfileUpdateForm validBasicInfo() {
        ProfileUpdateForm form = new ProfileUpdateForm();
        form.setEmail("member@cakeshop.local");
        form.setName("홍길동");
        form.setNickname("케이크러버");
        form.setPhone("010-1234-5678");
        return form;
    }
}
