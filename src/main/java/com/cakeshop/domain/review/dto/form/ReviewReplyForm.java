package com.cakeshop.domain.review.dto.form;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import lombok.Getter;
import lombok.Setter;

// 관리자가 후기에 다는 답글 입력을 담는 상자다. 담는 것은 내용 한 줄뿐이다.
// 답글 등록(POST .../replies)과 수정(POST .../replies/edit)이 같은 폼을 쓴다
//   -> 어느 후기인지는 URL의 reviewId가, 누가 썼는지는 로그인 정보가 정하므로 폼에 둘 필요가 없다
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
