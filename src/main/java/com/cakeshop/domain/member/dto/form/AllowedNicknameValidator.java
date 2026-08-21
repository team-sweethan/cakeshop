package com.cakeshop.domain.member.dto.form;

import com.cakeshop.domain.member.NicknamePolicy;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class AllowedNicknameValidator implements ConstraintValidator<AllowedNickname, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        return value == null || NicknamePolicy.isAllowed(value);
    }
}
