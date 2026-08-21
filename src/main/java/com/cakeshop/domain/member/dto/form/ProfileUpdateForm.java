package com.cakeshop.domain.member.dto.form;

import com.cakeshop.domain.member.dto.view.MemberProfileView;
import com.cakeshop.domain.member.NicknamePolicy;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ProfileUpdateForm {

    @NotBlank(message = "이름을 입력해 주세요.")
    @Size(max = 50, message = "이름은 50자 이하여야 합니다.")
    private String name;

    @NotBlank(message = "닉네임을 입력해 주세요.")
    @Size(max = 50, message = "닉네임은 50자 이하여야 합니다.")
    @AllowedNickname
    private String nickname;

    public void setNickname(String nickname) {
        this.nickname = NicknamePolicy.normalize(nickname);
    }

    @NotBlank(message = "전화번호를 입력해 주세요.")
    @Pattern(regexp = "^01[016789]-?\\d{3,4}-?\\d{4}$", message = "전화번호 형식을 확인해 주세요.")
    private String phone;

    @NotBlank(message = "이메일을 입력해 주세요.")
    @Email(message = "이메일 형식을 확인해 주세요.")
    @Size(max = 255, message = "이메일은 255자 이하여야 합니다.")
    private String email;

    @Size(max = 100, message = "현재 비밀번호는 100자 이하여야 합니다.")
    private String currentPassword;

    @Size(max = 100, message = "새 비밀번호는 100자 이하여야 합니다.")
    private String newPassword;

    @Size(max = 100, message = "새 비밀번호 확인은 100자 이하여야 합니다.")
    private String newPasswordConfirm;

    public static ProfileUpdateForm from(MemberProfileView member) {
        ProfileUpdateForm form = new ProfileUpdateForm();
        form.setEmail(member.email());
        form.setName(member.name());
        form.setNickname(member.nickname());
        form.setPhone(member.phone());
        return form;
    }

    @AssertTrue(message = "비밀번호를 변경하려면 현재 비밀번호와 새 비밀번호 확인을 모두 입력해 주세요.")
    public boolean isPasswordChangeComplete() {
        return !isPasswordChangeRequested()
                || hasText(currentPassword) && hasText(newPassword) && hasText(newPasswordConfirm);
    }

    @AssertTrue(message = "새 비밀번호는 8자 이상이며 영문, 숫자, 특수문자를 각각 포함해야 합니다.")
    public boolean isNewPasswordValid() {
        return !isPasswordChangeRequested()
                || newPassword != null
                && newPassword.matches("^(?=.*[A-Za-z])(?=.*\\d)(?=.*[^A-Za-z\\d\\s])\\S{8,100}$");
    }

    @AssertTrue(message = "새 비밀번호가 일치하지 않습니다.")
    public boolean isNewPasswordConfirmed() {
        return !isPasswordChangeRequested()
                || newPassword == null
                || newPasswordConfirm == null
                || newPassword.equals(newPasswordConfirm);
    }

    public boolean isPasswordChangeRequested() {
        return hasText(currentPassword) || hasText(newPassword) || hasText(newPasswordConfirm);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
