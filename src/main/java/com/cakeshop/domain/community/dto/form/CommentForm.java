package com.cakeshop.domain.community.dto.form;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
/**
 * ******************************
 * 작성자 : HyunGyu-Cho
 * 담당자 : 현규
 * 작성일 : 2026-08-05
 * 기능 : 커뮤니티 입력값 검증
 * 설명 : CommentForm 요청 데이터와 검증 규칙을 정의한다.
 * ******************************
 */
// 상세 화면의 댓글 입력 칸에서 넘어온 값을 담는 상자다.
// 아래 @NotBlank·@Size 는 이 클래스가 스스로 돌리지 않는다 — 컨트롤러가 매개변수를
// @Valid @ModelAttribute("commentForm") CommentForm form 으로 받을 때 스프링이 검사하고,
// 결과는 바로 뒤의 BindingResult 로 들어간다. hasErrors()면 저장하지 않고 화면으로 되돌린다.
public class CommentForm {

    @NotBlank(message = "댓글 내용을 입력해 주세요.")
    @Size(max = 500, message = "댓글은 500자 이하여야 합니다.")
    private String content;

    // @Setter 가 만들어 줄 setContent 를 직접 써서 덮어쓴다.
    // 스프링이 요청 값을 넣을 때 이 setter 를 지나가므로, 공백만 친 댓글은 ""가 되어 @NotBlank 에 걸린다.
    public void setContent(String content) {
        this.content = content == null ? null : content.strip();
    }
}
