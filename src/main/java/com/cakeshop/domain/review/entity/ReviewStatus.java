package com.cakeshop.domain.review.entity;

public enum ReviewStatus {
    PUBLISHED,
    DELETED,
    BLOCKED;

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
