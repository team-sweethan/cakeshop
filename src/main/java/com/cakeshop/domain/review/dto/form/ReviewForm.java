package com.cakeshop.domain.review.dto.form;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import lombok.Getter;
import lombok.Setter;

/**
 * ******************************
 * 작성자 : HyunGyu-Cho
 * 담당자 : 현규
 * 작성일 : 2026-08-06
 * 기능 : 후기 작성 입력
 * 설명 : 후기 등록 폼 값을 담는다. 조각 1(#109).
 * ******************************
 *
 * <p>{@code memberId} 와 {@code productId} 는 받지 않는다. 회원은 인증에서, 상품은 주문 계약이
 * 돌려준 주문 상품에서 가져온다. 요청값을 믿으면 남의 상품에 후기를 붙일 수 있다(PLAN R4).
 *
 * <p>평점 범위는 화면·서버·DB 세 겹으로 막는다. 이 폼 검증은 그중 서버 겹이고, DB 겹은
 * 조각 0 의 {@code chk_reviews_*_rating} 이다.
 */
@Getter
@Setter
public class ReviewForm {

    @NotNull(message = "주문 상품을 선택해 주세요.")
    private Long orderItemId;

    @NotNull(message = "전체 평점을 선택해 주세요.")
    @Min(value = 1, message = "평점은 1점에서 5점 사이여야 합니다.")
    @Max(value = 5, message = "평점은 1점에서 5점 사이여야 합니다.")
    private Integer overallRating;

    @NotNull(message = "맛 평점을 선택해 주세요.")
    @Min(value = 1, message = "평점은 1점에서 5점 사이여야 합니다.")
    @Max(value = 5, message = "평점은 1점에서 5점 사이여야 합니다.")
    private Integer tasteRating;

    @NotNull(message = "디자인 평점을 선택해 주세요.")
    @Min(value = 1, message = "평점은 1점에서 5점 사이여야 합니다.")
    @Max(value = 5, message = "평점은 1점에서 5점 사이여야 합니다.")
    private Integer designRating;

    @NotNull(message = "응대 평점을 선택해 주세요.")
    @Min(value = 1, message = "평점은 1점에서 5점 사이여야 합니다.")
    @Max(value = 5, message = "평점은 1점에서 5점 사이여야 합니다.")
    private Integer serviceRating;

    @NotBlank(message = "후기 내용을 입력해 주세요.")
    @Size(min = 10, max = 2000, message = "후기는 10자 이상 2000자 이하로 입력해 주세요.")
    private String content;
}
