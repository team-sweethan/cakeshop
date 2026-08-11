package com.cakeshop.domain.community.dto.view;

/**
 * ******************************
 * 작성자 : HyunGyu-Cho
 * 담당자 : 현규
 * 작성일 : 2026-08-05
 * 기능 : 커뮤니티 화면 데이터 전달
 * 설명 : AdminPostSort 화면에 전달할 데이터를 정의한다.
 * ******************************
 */
public enum AdminPostSort {

    LATEST("LATEST", "최신순"),

    REPORTS("REPORTS", "신고 많은 순");

    private final String parameter;
    private final String label;

    AdminPostSort(String parameter, String label) {
        this.parameter = parameter;
        this.label = label;
    }

    public String getParameter() {
        return parameter;
    }

    public String getLabel() {
        return label;
    }

    public static AdminPostSort from(String value) {
        if (value == null) {
            return LATEST;
        }

        for (AdminPostSort sort : values()) {
            if (sort.name().equalsIgnoreCase(value.trim())) {
                return sort;
            }
        }

        return LATEST;
    }
}
