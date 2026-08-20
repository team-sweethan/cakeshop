package com.cakeshop.domain.community.dto.view;

// 고객 목록 화면의 정렬 기준. 주소로 들어온 sort 문자열을 이 enum 으로 한 번 받아서 넘긴다.
// 예시 요청: GET /community?sort=LIKES -> PostSort.LIKES -> SQL 의 <choose> 분기가 갈린다.
// 허용값이 enum 상수 셋으로 정해져 있어, 사용자가 적은 문자열이 SQL 로 흘러들 길이 없다.
public enum PostSort {

    // enum 상수도 객체 하나다. LATEST 는 parameter="LATEST", label="최신순" 을 가진 PostSort 인스턴스이고
    // 셋은 클래스가 처음 쓰일 때 한 번만 만들어진다.
    LATEST("LATEST", "최신순"),   // 기본값
    VIEWS("VIEWS", "조회수순"),
    LIKES("LIKES", "좋아요순");

    private final String parameter;
    private final String label;

    // enum 생성자는 항상 private 이라 밖에서 new PostSort(...) 를 할 수 없다.
    // 위에 적은 상수 셋을 만들 때만 불린다.
    PostSort(String parameter, String label) {
        this.parameter = parameter;
        this.label = label;
    }

    // String -> PostSort 로 바꾸는 입구. 잘못된 입력을 예외로 만들지 않고 전부 기본값 LATEST 로 떨어뜨린다.
    // null, "", "   ", "abc" -> LATEST / "likes", " Likes " -> LIKES (trim + equalsIgnoreCase 라 대소문자·공백은 봐준다)
    public static PostSort from(String value) {
        if (value == null) {
            return LATEST;
        }

        // values(): 컴파일러가 enum 마다 만들어 주는 메서드로, 상수 전부를 배열로 준다.
        for (PostSort sort : values()) {
            if (sort.name().equalsIgnoreCase(value.trim())) {
                return sort;
            }
        }

        return LATEST;
    }

    // 화면이 정렬 링크와 선택 상자를 그릴 때 쓴다 (parameter = 주소에 실을 값, label = 사람이 읽을 이름).
    public String getParameter() {
        return parameter;
    }

    public String getLabel() {
        return label;
    }
}
