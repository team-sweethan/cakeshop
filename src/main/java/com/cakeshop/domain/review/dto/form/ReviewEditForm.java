package com.cakeshop.domain.review.dto.form;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import lombok.Getter;
import lombok.Setter;

// 후기 수정 화면(customer/review/edit)의 입력을 담는 상자다. POST /reviews/{reviewId}/edit 이 여기에 채워진다.
// ReviewWriteForm과 평점·내용 필드는 같지만 orderItemId와 images가 없다
//   -> 어느 주문의 후기인지는 URL의 reviewId로 이미 정해졌고, 사진은 수정에서 다루지 않는다
// 수정 폼을 열 때는 반대 방향으로도 쓰인다: Controller가 기존 값을 setter로 넣어 화면에 채워 보낸다
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
