package com.cakeshop.domain.review.dto.form;

import java.util.ArrayList;
import java.util.List;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import lombok.Getter;
import lombok.Setter;

import org.springframework.web.multipart.MultipartFile;

@Getter
@Setter
public class ReviewWriteForm {

    // 상품 식별자는 여기에 두지 않는다. 요청값을 믿으면 남의 상품에 후기를 붙일 수 있어
    // Service 가 order_items 에서 파생시킨다 (R4).
    @NotNull(message = "후기를 작성할 주문 상품을 선택해 주세요.")
    private Long orderItemId;

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

    // 장수·파일 형식·용량은 파일 내용과 저장 순서를 함께 보는 Service가 검증한다.
    private List<MultipartFile> images = new ArrayList<>();

    public void setContent(String content) {
        this.content = content == null ? null : content.strip();
    }
}
