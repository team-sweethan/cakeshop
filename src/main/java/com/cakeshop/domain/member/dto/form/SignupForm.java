package com.cakeshop.domain.member.dto.form;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SignupForm {
    private String email;
    private String password;
    private String name;
    private String nickname;
    private String phone;
}
