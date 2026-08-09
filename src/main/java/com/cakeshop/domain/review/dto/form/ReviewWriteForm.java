package com.cakeshop.domain.review.dto.form;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * ******************************
 * 작성자 : HyunGyu-Cho
 * 담당자 : 현규
 * 작성일 : 2026-08-09
 * 기능 : 후기 작성 입력값 검증
 * 설명 : 평점 4종과 본문을 받는다. productId 는 받지 않는다 — 주문 상품에서 파생시킨다(R4).
 * ******************************
 */
@Getter
@Setter
public class ReviewWriteForm {

    @NotNull(message = "주문 상품을 선택해 주세요.")
    @Positive(message = "주문 상품을 선택해 주세요.")
    private Long orderItemId;

    // 기본 선택을 두지 않으므로 미선택은 null 로 온다(DOMAIN.md 2.2).
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

    /** 앞뒤만 다듬는다 — 중간 줄바꿈은 보존한다(DOMAIN.md 2.4). */
    public void setContent(String content) {
        this.content = content == null ? null : content.strip();
    }
}
