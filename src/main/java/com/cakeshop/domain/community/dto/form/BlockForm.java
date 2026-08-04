package com.cakeshop.domain.community.dto.form;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 관리자 차단 화면의 입력값과 Bean Validation 규칙을 담는다.
 *
 * 사유가 필수인 것은 그 값이 작성자에게 그대로 보이기 때문이다(docs/community/DOMAIN.md
 * 4.3). 차단된 글에 작성자가 할 수 있는 일은 없으므로(4.2), 사유가 비어 있으면 글이 왜
 * 막혔는지 영영 알 수 없다. 차단이 처벌이 아니라 교정으로 작동하려면 사유가 전달되어야 한다.
 *
 * 차단 해제에는 폼이 없다. 해제는 blocked_* 를 되돌리지 않고 상태만 바꾸므로 입력값이 없다.
 */
@Getter
@Setter
public class BlockForm {

    @NotBlank(message = "차단 사유를 입력해 주세요.")
    @Size(max = 500, message = "차단 사유는 500자 이하여야 합니다.")
    private String reason;

    /** 앞뒤 공백만 제거한다. 중간 줄바꿈은 관리자가 쓴 그대로 보존한다(DOMAIN.md 7). */
    public void setReason(String reason) {
        this.reason = reason == null ? null : reason.strip();
    }
}
