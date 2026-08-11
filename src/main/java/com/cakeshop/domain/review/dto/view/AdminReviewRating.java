package com.cakeshop.domain.review.dto.view;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum AdminReviewRating {

    ALL(null, null, null),
    FIVE("5", 5, 5),
    FOUR("4", 4, 4),
    THREE_OR_LESS("3", null, 3);

    private final String parameter;
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
