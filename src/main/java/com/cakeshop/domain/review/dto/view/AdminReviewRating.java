package com.cakeshop.domain.review.dto.view;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum AdminReviewRating {

    ALL("", "평점 전체", null, null),
    FIVE("5", "5점", 5, 5),
    FOUR("4", "4점", 4, 4),
    THREE_OR_LESS("3", "3점 이하", null, 3);

    private final String parameter;
    private final String label;
    private final Integer min;
    private final Integer max;

    // 모르는 값은 오류가 아니라 전체로 떨어뜨린다 (specs/review-admin.md C2).
    public static AdminReviewRating from(String parameter) {
        if (parameter == null) {
            return ALL;
        }

        String normalized = parameter.trim();

        for (AdminReviewRating rating : values()) {
            if (normalized.equals(rating.parameter)) {
                return rating;
            }
        }

        return ALL;
    }

}
