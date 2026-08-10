package com.cakeshop.domain.review.dto.form;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ReviewEditForm {

    @NotNull(message = "전체 평점을 선택해 주세요.")
    @Min(value = 1, message = "평점은 1~5 사이여야 합니다.")
    @Max(value = 5, message = "평점은 1~5 사이여야 합니다.")
    private Integer overallRating;

    @NotNull(message = "맛 평점을 선택해 주세요.")
    @Min(value = 1, message = "평점은 1~5 사이여야 합니다.")
    @Max(value = 5, message = "평점은 1~5 사이여야 합니다.")
    private Integer tasteRating;

    @NotNull(message = "디자인 평점을 선택해 주세요.")
    @Min(value = 1, message = "평점은 1~5 사이여야 합니다.")
    @Max(value = 5, message = "평점은 1~5 사이여야 합니다.")
    private Integer designRating;

    @NotNull(message = "응대 평점을 선택해 주세요.")
    @Min(value = 1, message = "평점은 1~5 사이여야 합니다.")
    @Max(value = 5, message = "평점은 1~5 사이여야 합니다.")
    private Integer serviceRating;

    @NotBlank(message = "후기 내용을 입력해 주세요.")
    @Size(min = 10, max = 2000, message = "후기는 10자 이상 2000자 이하여야 합니다.")
    private String content;

    public void setContent(String content) {
        this.content = content == null ? null : content.strip();
    }
}
