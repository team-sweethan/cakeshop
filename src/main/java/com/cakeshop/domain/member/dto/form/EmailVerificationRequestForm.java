package com.cakeshop.domain.member.dto.form;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class EmailVerificationRequestForm {

    @NotBlank
    @Email
    private String email;
}
