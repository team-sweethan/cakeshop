package com.cakeshop.domain.review.entity;

// 후기가 가질 수 있는 상태 세 가지. reviews.status 컬럼과 짝이다
public enum ReviewStatus {

    // enum 상수는 "이 클래스의 객체 3개"다. 괄호 안 값이 아래 생성자로 들어간다
    //     PUBLISHED("노출 중") -> new ReviewStatus("노출 중") 이 딱 한 번 만들어진다
    // 그래서 ReviewStatus.PUBLISHED.getLabel() 은 "노출 중" 이 된다
    PUBLISHED("노출 중"),
    DELETED("삭제됨"),
    BLOCKED("숨김");

    private final String label;

    // enum 생성자는 적지 않아도 private 이다. 밖에서 새 상태를 만들 수 없다
    ReviewStatus(String label) {
        this.label = label;
    }

    // 화면에 코드값(PUBLISHED) 대신 사람이 읽을 말(노출 중)을 뿌릴 때 쓴다
    public String getLabel() {
        return label;
    }

    // 지금 상태(this)에서 next 로 넘어가도 되는지만 답한다. 값을 바꾸지는 않는다
    // 갈 수 있는 길:
    //     PUBLISHED -> DELETED (작성자 삭제) / BLOCKED (관리자 숨김)
    //     BLOCKED   -> PUBLISHED (숨김 해제)
    //     DELETED   -> 없음. 한 번 삭제되면 끝 상태다
    // switch 식(->): case 마다 값을 내놓고 그 값이 그대로 return 된다 (break 가 없다)
    // enum 을 대상으로 하면 상수를 다 덮었는지 컴파일러가 검사한다 -> 상태를 늘리면 여기서 컴파일이 깨진다
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
