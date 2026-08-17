package com.cakeshop.domain.dashboard.dto.view;

/** 관리자 대시보드에서 오늘 픽업 일정의 긴급도를 나타낸다. */
public enum PickupUrgency {
    OVERDUE("지연"),
    IMMINENT("임박"),
    SCHEDULED("예정");

    private final String label;

    PickupUrgency(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
