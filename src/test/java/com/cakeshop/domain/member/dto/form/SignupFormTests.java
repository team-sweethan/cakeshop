package com.cakeshop.domain.member.dto.form;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.time.LocalDate;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SignupFormTests {

    private final Validator validator =
            Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void validate_blankAndInvalidMemberInput_rejectsForm() {
        SignupForm form = new SignupForm();
        form.setEmail("invalid-email");
        form.setPassword("password");
        form.setPasswordConfirm("different");
        form.setName(" ");
        form.setNickname(" ");
        form.setPhone("1234");

        Set<ConstraintViolation<SignupForm>> violations = validator.validate(form);

        assertThat(violations)
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains(
                        "email",
                        "password",
                        "passwordConfirmed",
                        "name",
                        "nickname",
                        "phone",
                        "birthDate");
    }

    @Test
    void validate_validMemberInput_acceptsForm() {
        SignupForm form = new SignupForm();
        form.setEmail("member@cakeshop.local");
        form.setPassword("Password1!");
        form.setPasswordConfirm("Password1!");
        form.setName("홍길동");
        form.setNickname("케이크러버");
        form.setPhone("010-1234-5678");
        form.setBirthDate(LocalDate.of(2000, 1, 15));

        assertThat(validator.validate(form)).isEmpty();
    }

    @Test
    void validate_emailWithoutTopLevelDomain_rejectsForm() {
        SignupForm form = new SignupForm();
        form.setEmail("member@domain");
        form.setPassword("Password1!");
        form.setPasswordConfirm("Password1!");
        form.setName("홍길동");
        form.setNickname("케이크러버");
        form.setPhone("010-1234-5678");
        form.setBirthDate(LocalDate.of(2000, 1, 15));

        assertThat(validator.validate(form))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("email");
    }

    @Test
    void validate_nameLongerThanDatabaseLimit_rejectsForm() {
        SignupForm form = new SignupForm();
        form.setEmail("member@cakeshop.local");
        form.setPassword("Password1!");
        form.setPasswordConfirm("Password1!");
        form.setName("가".repeat(51));
        form.setNickname("케이크러버");
        form.setPhone("010-1234-5678");
        form.setBirthDate(LocalDate.of(2000, 1, 15));

        assertThat(validator.validate(form))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("name");
    }

    @Test
    void validate_birthDateToday_rejectsForm() {
        SignupForm form = validSignupForm();
        form.setBirthDate(LocalDate.now());

        assertThat(validator.validate(form))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("birthDate");
    }

    @Test
    void validate_birthDateFuture_rejectsForm() {
        SignupForm form = validSignupForm();
        form.setBirthDate(LocalDate.now().plusDays(1));

        assertThat(validator.validate(form))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("birthDate");
    }

    private SignupForm validSignupForm() {
        SignupForm form = new SignupForm();
        form.setEmail("member@cakeshop.local");
        form.setPassword("Password1!");
        form.setPasswordConfirm("Password1!");
        form.setName("홍길동");
        form.setNickname("케이크러버");
        form.setPhone("010-1234-5678");
        form.setBirthDate(LocalDate.of(2000, 1, 15));
        return form;
    }
}
