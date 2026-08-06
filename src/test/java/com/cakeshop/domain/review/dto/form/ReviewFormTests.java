package com.cakeshop.domain.review.dto.form;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

/**
 * 후기 본문 검증이 <b>trim 후 값</b>을 본다는 것을 고정한다(SPEC 2.4).
 *
 * <p>trim 을 저장 직전에 하면 이 경계가 전부 통과한다 — 길이는 원문으로 재고 저장만 줄어들어
 * 화면에서야 짧은 후기가 드러난다.
 */
class ReviewFormTests {

    private final Validator validator =
            Validation.buildDefaultValidatorFactory().getValidator();

    /** 공백 일곱 개 + 세 글자는 원문 10자지만 실제 내용은 세 글자다. 거부해야 한다. */
    @Test
    void validate_paddedShortContent_isRejected() {
        ReviewForm form = form("       맛있다");

        assertThat(validator.validate(form))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("content");
    }

    /** 공백만 입력은 거부한다. */
    @Test
    void validate_blankContent_isRejected() {
        ReviewForm form = form("                    ");

        assertThat(validator.validate(form))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("content");
    }

    /** 앞뒤 공백을 걷어도 10자를 넘으면 통과한다. */
    @Test
    void validate_paddedLongEnoughContent_isAccepted() {
        ReviewForm form = form("  케이크가 정말 맛있었습니다  ");

        assertThat(validator.validate(form)).isEmpty();
    }

    /** 앞뒤만 걷고 <b>중간 줄바꿈은 보존한다.</b> 여러 줄로 쓴 후기가 뭉치면 안 된다. */
    @Test
    void setContent_stripsEdgesButKeepsLineBreaks() {
        ReviewForm form = form("\n  맛있었습니다\n\n또 시킬게요  \n");

        assertThat(form.getContent()).isEqualTo("맛있었습니다\n\n또 시킬게요");
    }

    /** 상한 2000자는 걷힌 값 기준이다. */
    @Test
    void validate_contentOverLimitAfterStrip_isRejected() {
        ReviewForm form = form("  " + "가".repeat(2001) + "  ");

        assertThat(validator.validate(form))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("content");
    }

    private ReviewForm form(String content) {
        ReviewForm form = new ReviewForm();
        form.setOrderItemId(1L);
        form.setOverallRating(5);
        form.setTasteRating(5);
        form.setDesignRating(5);
        form.setServiceRating(5);
        form.setContent(content);
        return form;
    }
}
