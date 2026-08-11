package com.cakeshop.domain.review.entity;

public enum ReviewStatus {
    PUBLISHED("노출 중"),
    DELETED("삭제됨"),
    BLOCKED("숨김");

    private final String label;

    ReviewStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public boolean canTransitionTo(ReviewStatus next) {
        if (next == null) {
            return false;
        }
        return switch (this) {
            case PUBLISHED -> next == DELETED || next == BLOCKED;
            case BLOCKED -> next == PUBLISHED;
            case DELETED -> false;
        };
    }
}
