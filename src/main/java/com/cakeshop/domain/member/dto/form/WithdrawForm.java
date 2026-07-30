package com.cakeshop.domain.member.dto.form;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class WithdrawForm {

    @NotBlank(message = "현재 비밀번호를 입력해 주세요.")
    @Size(max = 100, message = "현재 비밀번호는 100자 이하여야 합니다.")
    private String currentPassword;

    @AssertTrue(message = "회원 탈퇴 안내를 확인해 주세요.")
    private boolean withdrawalConfirmed;
}
