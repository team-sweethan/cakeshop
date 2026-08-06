package com.cakeshop.domain.community.dto.form;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
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
 * 설명 : PostForm 요청 데이터와 검증 규칙을 정의한다.
 * ******************************
 */
public class PostForm {

    @NotNull(message = "분류를 선택해 주세요.")
    @Positive(message = "분류를 선택해 주세요.")
    private Long categoryId;

    @NotBlank(message = "제목을 입력해 주세요.")
    @Size(max = 100, message = "제목은 100자 이하여야 합니다.")
    private String title;

    @NotBlank(message = "내용을 입력해 주세요.")
    @Size(max = 5000, message = "내용은 5000자 이하여야 합니다.")
    private String content;

    public void setTitle(String title) {
        this.title = strip(title);
    }

    public void setContent(String content) {
        this.content = strip(content);
    }

    private String strip(String value) {
        return value == null ? null : value.strip();
    }
}
