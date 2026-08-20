package com.cakeshop.domain.community.entity;

/**
 * ******************************
 * 작성자 : HyunGyu-Cho
 * 담당자 : 현규
 * 작성일 : 2026-08-05
 * 기능 : 커뮤니티 도메인 모델
 * 설명 : PostStatus 도메인의 상태와 값을 정의한다.
 * ******************************
 */
// enum 상수 하나하나가 이 타입의 인스턴스다. 셋 말고 다른 PostStatus 는 존재할 수 없다
// 괄호 안 값은 아래 생성자의 label 로 넘어간다
public enum PostStatus {
    PUBLISHED("노출 중"),
    DELETED("삭제됨"),
    BLOCKED("차단됨");

    private final String label;

    // enum 생성자는 private 을 안 써도 자동으로 private 이다
    // 그래서 new PostStatus("보류") 처럼 네 번째 값을 만들어 낼 방법이 없다
    PostStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    // 어떤 값에서 어떤 값으로 갈 수 있는지를 코드로 적은 표
    // PUBLISHED -> DELETED, PUBLISHED -> BLOCKED
    // BLOCKED   -> PUBLISHED
    // DELETED   -> 어디로도 못 감 (항상 false)
    public boolean canTransitionTo(PostStatus next) {
        if (next == null) {
            return false;
        }
        // switch 식(->): case 마다 값을 돌려주고 그 값이 곧 return 값이 된다
        // enum 의 모든 상수를 다 적었으므로 default 없이도 컴파일된다
        // 나중에 상수를 하나 더 만들면 여기서 컴파일 오류가 나서 빠뜨릴 수 없다
        return switch (this) {
            case PUBLISHED -> next == DELETED || next == BLOCKED;
            case BLOCKED -> next == PUBLISHED;
            case DELETED -> false;
        };
    }
}
