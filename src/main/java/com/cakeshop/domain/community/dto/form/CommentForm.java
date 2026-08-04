package com.cakeshop.domain.community.dto.form;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 댓글 작성 화면의 입력값과 Bean Validation 규칙을 담는다.
 *
 * 담는 것이 본문 하나뿐인 이유는 댓글에 수정이 없어서다(docs/community/DOMAIN.md 6.4).
 * 대상 게시글과 작성자는 주소와 인증 정보에서 오므로 폼에 두지 않는다 — 두면 요청으로
 * 남의 회원 번호를 실어 보낼 자리가 생긴다.
 *
 * setter가 값을 다듬는 것은 PostForm과 같은 이유다. DOMAIN.md 7이 "trim 후 검증"을
 * 요구하는데, 검증은 필드 값을 보고 돌아가므로 들어올 때 다듬지 않으면 공백만 입력한
 * 댓글이 길이 검사를 통과한 뒤 공백 그대로 저장된다.
 */
@Getter
@Setter
public class CommentForm {

    @NotBlank(message = "댓글 내용을 입력해 주세요.")
    @Size(max = 500, message = "댓글은 500자 이하여야 합니다.")
    private String content;

    /** 앞뒤 공백만 제거한다. 중간 줄바꿈은 사용자가 쓴 그대로 보존해야 한다. */
    public void setContent(String content) {
        this.content = content == null ? null : content.strip();
    }
}
