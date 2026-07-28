package com.cakeshop.domain.member.dto.form;

import lombok.Data;

@Data
public class ProfileUpdateForm {

    // 기본 정보
    private String name;
    private String phone;
    private String email;

    // 비밀번호 변경 정보
    private String currentPassword; // 현재 비밀번호 확인용
    private String newPassword;     // 변경할 새 비밀번호
    private String newPasswordConfirm; // 비밀번호 확인용
}