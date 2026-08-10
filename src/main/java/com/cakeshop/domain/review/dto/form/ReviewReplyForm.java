package com.cakeshop.domain.review.dto.form;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ReviewReplyForm {

    @NotBlank(message = "답글 내용을 입력해 주세요.")
    @Size(max = 1000, message = "답글은 1000자 이하여야 합니다.")
    private String content;

    public void setContent(String content) {
        this.content = content == null ? null : content.strip();
    }
}
