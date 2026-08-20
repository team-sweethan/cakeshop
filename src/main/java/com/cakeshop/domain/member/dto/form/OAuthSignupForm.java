package com.cakeshop.domain.member.dto.form;

import com.cakeshop.domain.member.service.NicknamePolicy;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

/** Google 인증 뒤 필요한 회원 정보를 입력받는다. */
@Getter
@Setter
public class OAuthSignupForm {

    @NotBlank(message = "이름을 입력해 주세요.")
    @Size(max = 50, message = "이름은 50자 이하여야 합니다.")
    private String name;

    @NotBlank(message = "닉네임을 입력해 주세요.")
    @Size(max = 50, message = "닉네임은 50자 이하여야 합니다.")
    @Pattern(regexp = "^(?!\\s*관리자\\s*$).*$", message = "관리자는 닉네임으로 사용할 수 없습니다.")
    private String nickname;

    public void setNickname(String nickname) {
        this.nickname = NicknamePolicy.normalize(nickname);
    }

    @NotBlank(message = "전화번호를 입력해 주세요.")
    @Pattern(regexp = "^01[016789]-?\\d{3,4}-?\\d{4}$", message = "전화번호 형식을 확인해 주세요.")
    private String phone;

    @NotNull(message = "생년월일을 입력해 주세요.")
    @Past(message = "생년월일은 과거 날짜여야 합니다.")
    private LocalDate birthDate;
}
