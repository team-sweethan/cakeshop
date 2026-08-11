package com.cakeshop.domain.review.dto.view;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

import org.junit.jupiter.api.Test;

import com.cakeshop.domain.review.entity.ReviewStatus;

class ReviewDisplayLabelTests {

    @Test
    void reviewStatus_labelsMatchScreens() {
        assertThat(ReviewStatus.values())
                .extracting(ReviewStatus::getLabel)
                .containsExactly("노출 중", "삭제됨", "숨김");
    }

    @Test
    void adminRatingOptionsOwnRequestParameterAndLabel() {
        assertThat(AdminReviewRating.values())
                .extracting(AdminReviewRating::getParameter, AdminReviewRating::getLabel)
                .containsExactly(
                        tuple("", "평점 전체"),
                        tuple("5", "5점"),
                        tuple("4", "4점"),
                        tuple("3", "3점 이하"));
    }
}
