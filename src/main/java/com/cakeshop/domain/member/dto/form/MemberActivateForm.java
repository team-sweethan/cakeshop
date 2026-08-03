package com.cakeshop.domain.member.dto.form;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MemberActivateForm {

    @NotBlank(message = "이용정지 해제 사유를 입력해 주세요.")
    @Size(max = 500, message = "이용정지 해제 사유는 500자 이내로 입력해 주세요.")
    private String reason;
}
