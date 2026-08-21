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

        signupForm.setNickname("\u00A0관리자\u00A0");
        profileUpdateForm.setNickname("\u202F관리자\u202F");
        oauthSignupForm.setNickname("관리자");

        assertNicknameIsRejected(signupForm, signupForm.getNickname());
        assertNicknameIsRejected(profileUpdateForm, profileUpdateForm.getNickname());
        assertNicknameIsRejected(oauthSignupForm, oauthSignupForm.getNickname());
    }

    @Test
    void allMemberNicknameForms_rejectReservedAdministratorNameWithInvisibleCharacter() {
        SignupForm signupForm = new SignupForm();
        ProfileUpdateForm profileUpdateForm = new ProfileUpdateForm();
        OAuthSignupForm oauthSignupForm = new OAuthSignupForm();

        signupForm.setNickname("관\u200B리자");
        profileUpdateForm.setNickname("관\u034F리자");
        oauthSignupForm.setNickname("ᄀ\u034Fᅪᆫ리자");

        assertNicknameIsRejected(signupForm, signupForm.getNickname());
        assertNicknameIsRejected(profileUpdateForm, profileUpdateForm.getNickname());
        assertNicknameIsRejected(oauthSignupForm, oauthSignupForm.getNickname());
    }

    private void assertNicknameIsRejected(Object form, String nickname) {
        assertThat(validator.validate(form))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("nickname");
    }
}
