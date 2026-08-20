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
// 관리자 게시글 목록의 정렬 기준을 담는 enum 이다.
// 상수 하나가 값 두 개를 같이 들고 다닌다: parameter(URL에 실릴 값)와 label(화면에 보일 한글).
// 예시 요청: GET /admin/community?sort=REPORTS -> 화면 선택 상자에는 "신고 많은 순"으로 뜬다.
public enum AdminPostSort {

    // 상수 뒤의 괄호가 아래 생성자 AdminPostSort(parameter, label) 호출이다.
    LATEST("LATEST", "최신순"),

    REPORTS("REPORTS", "신고 많은 순");

    private final String parameter;
    private final String label;

    // enum 생성자는 언제나 private 다. 그래서 상수 목록 밖에서는 새로 만들 수 없다.
    AdminPostSort(String parameter, String label) {
        this.parameter = parameter;
        this.label = label;
    }

    // 화면에서 온 문자열 -> enum 상수로 바꾸는 입구다.
    // "reports", " REPORTS " 처럼 대소문자·공백이 달라도 받아 주고,
    // null 이거나 모르는 값이면 예외 대신 기본값 LATEST 로 떨어뜨린다.
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

    public String getParameter() {
        return parameter;
    }

    public String getLabel() {
        return label;
    }
}
