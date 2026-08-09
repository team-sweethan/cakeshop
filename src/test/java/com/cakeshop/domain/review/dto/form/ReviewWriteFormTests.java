package com.cakeshop.domain.review.dto.form;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

import org.junit.jupiter.api.Test;

/** 평점 범위와 본문 길이를 서버에서 막는지 확인한다 — 화면 검증은 폼을 거친 요청만 막는다. */
class ReviewWriteFormTests {

    private static final Validator VALIDATOR;

    static {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            VALIDATOR = factory.getValidator();
        }
    }

    @Test
    void validate_whenRatingAboveFive_reportsViolation() {
        ReviewWriteForm form = valid();
        form.setOverallRating(6);

        assertThat(fieldsInViolation(form)).containsExactly("overallRating");
    }

    @Test
    void validate_whenRatingBelowOne_reportsViolation() {
        ReviewWriteForm form = valid();
        form.setTasteRating(0);

        assertThat(fieldsInViolation(form)).containsExactly("tasteRating");
    }

    @Test
    void validate_whenRatingMissing_reportsViolation() {
        ReviewWriteForm form = valid();
        // 기본 선택을 두지 않으므로 손대지 않은 평점은 null 로 온다(DOMAIN.md 2.2).
        form.setDesignRating(null);

        assertThat(fieldsInViolation(form)).containsExactly("designRating");
    }

    @Test
    void validate_whenContentIsOnlyWhitespace_reportsViolation() {
        ReviewWriteForm form = valid();
        form.setContent("          ");

        assertThat(fieldsInViolation(form)).containsExactly("content");
    }

    @Test
    void validate_whenContentShorterThanTen_reportsViolation() {
        ReviewWriteForm form = valid();
        form.setContent("짧은후기");

        assertThat(fieldsInViolation(form)).containsExactly("content");
    }

    @Test
    void validate_whenAllValid_reportsNothing() {
        assertThat(fieldsInViolation(valid())).isEmpty();
    }

    private static Set<String> fieldsInViolation(ReviewWriteForm form) {
        return VALIDATOR.validate(form).stream()
                .map((ConstraintViolation<ReviewWriteForm> v) -> v.getPropertyPath().toString())
                .collect(java.util.stream.Collectors.toSet());
    }

    private static ReviewWriteForm valid() {
        ReviewWriteForm form = new ReviewWriteForm();
        form.setOrderItemId(42L);
        form.setOverallRating(5);
        form.setTasteRating(5);
        form.setDesignRating(4);
        form.setServiceRating(4);
        form.setContent("맛있게 잘 먹었습니다. 다음에 또 주문할게요.");
        return form;
    }
}
