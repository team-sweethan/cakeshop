package com.cakeshop.domain.member.dto.form;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

class NicknameValidationTests {

    private final Validator validator =
            Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void allMemberNicknameForms_rejectReservedAdministratorNameAfterTrimming() {
        SignupForm signupForm = new SignupForm();
        ProfileUpdateForm profileUpdateForm = new ProfileUpdateForm();
        OAuthSignupForm oauthSignupForm = new OAuthSignupForm();

        signupForm.setNickname(" 관리자 ");
        profileUpdateForm.setNickname(" 관리자 ");
        oauthSignupForm.setNickname(" 관리자 ");

        assertNicknameIsRejected(signupForm, signupForm.getNickname());
        assertNicknameIsRejected(profileUpdateForm, profileUpdateForm.getNickname());
        assertNicknameIsRejected(oauthSignupForm, oauthSignupForm.getNickname());
    }

    private void assertNicknameIsRejected(Object form, String nickname) {
        assertThat(nickname).isEqualTo("관리자");
        assertThat(validator.validate(form))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("nickname");
    }
}
