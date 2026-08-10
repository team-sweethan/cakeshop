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
public class ReportForm {

    @NotBlank(message = "신고 사유를 입력해 주세요.")
    @Size(max = 500, message = "신고 사유는 500자 이하여야 합니다.")
    private String reason;

    public void setReason(String reason) {
        this.reason = reason == null ? null : reason.strip();
    }
}
