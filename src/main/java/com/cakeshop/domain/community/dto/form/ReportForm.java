package com.cakeshop.domain.community.dto.form;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 게시글 신고 화면의 입력값과 Bean Validation 규칙을 담는다.
 *
 * 담는 것이 사유 하나뿐인 것은 CommentForm과 같은 이유다. 대상 게시글은 주소에서,
 * 신고자는 인증 정보에서 온다 — 폼에 두면 요청으로 남의 회원 번호를 실어 보낼 자리가 생긴다.
 *
 * 신고에는 수정도 취소도 없다(docs/community/DOMAIN.md 6.6). 그래서 이 폼이 채워지는
 * 경로는 작성 하나뿐이고, 기존 값을 실어 오는 자리가 없다.
 */
@Getter
@Setter
public class ReportForm {

    @NotBlank(message = "신고 사유를 입력해 주세요.")
    @Size(max = 500, message = "신고 사유는 500자 이하여야 합니다.")
    private String reason;

    /** 앞뒤 공백만 제거한다. 중간 줄바꿈은 사용자가 쓴 그대로 보존한다(DOMAIN.md 7). */
    public void setReason(String reason) {
        this.reason = reason == null ? null : reason.strip();
    }
}
