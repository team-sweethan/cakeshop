package com.cakeshop.domain.member.dto.form;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class EmailVerificationConfirmForm {

    @NotBlank
    @Email
    private String email;

    public void setEmail(String email) {
        this.email = SignupForm.normalizeEmail(email);
    }

    @NotBlank
    @Pattern(regexp = "\\d{6}")
    private String code;
}
