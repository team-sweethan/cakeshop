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
 * 설명 : ReportForm 요청 데이터와 검증 규칙을 정의한다.
 * ******************************
 */
// 게시글·댓글 신고 창에서 넘어온 사유를 담는 상자다.
// @NotBlank·@Size 는 컨트롤러가 @Valid @ModelAttribute("reportForm") 으로 받을 때 스프링이 검사하고,
// 그 결과는 바로 뒤의 BindingResult 로 들어간다. hasErrors()면 신고를 저장하지 않고 화면으로 되돌린다.
public class ReportForm {

    @NotBlank(message = "신고 사유를 입력해 주세요.")
    @Size(max = 500, message = "신고 사유는 500자 이하여야 합니다.")
    private String reason;

    // @Setter 가 만들어 줄 setter 를 직접 덮어쓴다. 공백만 적은 사유는 ""가 되어 @NotBlank 에 걸린다.
    public void setReason(String reason) {
        this.reason = reason == null ? null : reason.strip();
    }
}
