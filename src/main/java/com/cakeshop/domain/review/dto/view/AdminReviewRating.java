package com.cakeshop.domain.review.dto.view;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

// 관리자 후기 목록의 평점 필터 선택지. 화면의 <select> 항목이자 조회 조건이다
// 화면은 values() 로 전체 항목을 그리고, 요청은 parameter 문자열 하나만 보내온다
// @Getter + @RequiredArgsConstructor: 필드 넷의 읽기 메서드와 (parameter, label, min, max) 생성자를 롬복이 만든다
@Getter
@RequiredArgsConstructor
public enum AdminReviewRating {

    // (parameter, label, min, max)
    //     parameter : URL 에 실려 오는 값. 예시 요청: GET /admin/reviews?rating=3
    //     label     : 화면에 뿌릴 말
    //     min, max  : 조회 조건. null 은 "그쪽 끝은 열어 둔다" 는 뜻이다
    //         FIVE          -> 5 이상 5 이하 = 딱 5점
    //         THREE_OR_LESS -> 아래는 열고 3 이하
    //         ALL           -> 양쪽 다 null 이라 평점 조건을 아예 걸지 않는다
    ALL("", "평점 전체", null, null),
    FIVE("5", "5점", 5, 5),
    FOUR("4", "4점", 4, 4),
    THREE_OR_LESS("3", "3점 이하", null, 3);

    private final String parameter;
    private final String label;

    // int 가 아니라 Integer 인 이유가 여기 있다 — null 을 넣어 "조건 없음" 을 표현한다
    private final Integer min;
    private final Integer max;

    // 문자열 -> enum 변환. 컨트롤러가 받은 rating 파라미터를 이 메서드에 통과시켜 쓴다
    // values(): 위에 선언한 상수 넷을 배열로 돌려준다. 그중 parameter 가 같은 것을 찾는다
    // 목록에 없는 값이면 예외를 던지지 않고 ALL 을 돌려준다 (enum 의 valueOf 와 다른 점)
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
