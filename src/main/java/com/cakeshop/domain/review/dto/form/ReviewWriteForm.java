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

// 후기 작성 화면(customer/review/form)의 입력을 담는 상자다. POST /reviews 요청이 여기에 채워진다.
// @Getter/@Setter: Lombok이 필드마다 getXxx()/setXxx()를 만든다
//   -> 스프링은 그 setter 이름으로 요청 파라미터를 찾아 넣는다 (overallRating=5 -> setOverallRating(5))
// 평점이 int가 아니라 Integer인 이유: 아무것도 고르지 않았을 때 담을 null 자리가 필요하다
//   int면 비워도 0이 들어가서 @NotNull이 걸러 내지 못한다
// 각 검사의 message는 걸렸을 때 화면 th:errors 자리에 그대로 나오는 문구다
@Getter
@Setter
public class ReviewWriteForm {

    // 폼에 상품 식별자 칸이 없다 — Service 가 이 orderItemId 로 order_items 를 읽어 상품을 알아낸다
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

    // @Setter가 만들 setContent를 직접 써서 덮는다 (같은 이름이면 Lombok은 만들지 않는다)
    // 값이 들어오는 길목이 여기 하나라 검증도 저장도 다듬어진 값으로 돈다
    public void setContent(String content) {
        this.content = content == null ? null : content.strip();
    }
}
