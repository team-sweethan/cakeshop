# 조각 6 — 관리자 답글 (#113, PR #190 머지 완료)

> **끝난 조각의 기록이다. 지금 구속하지 않는다.**
> 이 조각이 만든 규칙의 정본은 `../specs/review-reply.md`.
> 조각 순서와 진행 상태는 `../PLAN.md`, 발견된 문제는 `../reviews/`,
> 방향을 고른 판단은 `../decisions/`에 있다.

**다른 도메인에 새로 만든 계약이 없다.** 답글은 `reviews`·`review_replies` 안에서 끝나고, 작성자 표시명·상품명은 조각 3·5가 이미 붙여 둔 C3 상세가 그대로 쓴다. 이 조각에서 남의 파일은 한 줄도 건드리지 않았다.

- `review_replies` INSERT. `uk_review_replies_review`가 후기당 1건을 강제하고, `DuplicateKeyException`을 `ALREADY_REPLIED`(409)로 바꾼다
- `admin_id`는 `members(id)` FK — 사람이 다는 것을 전제한다
- **고객 화면의 답글 노출도 이 조각이다**(B4) — B1(상품 후기 목록)과 B3(내 후기)의 각 후기 아래에 붙인다. 빼면 관리자는 답글을 쓰고 알림까지 나가는데 **고객이 들어올 화면에 답글이 없다**
- `PUBLISHED` 후기에만 답글을 달고, 확인과 INSERT를 한 문장에 묶는다(`../specs/review-reply.md` C5)
- **C6 수정도 `PUBLISHED`로 맞췄다** — spec에 상태 조건이 비어 있던 자리다(아래 결정 로그)
- `ReviewErrorCode`에 `ALREADY_REPLIED`(409)·`REPLY_NOT_FOUND`(404)가 들어왔다(`../DOMAIN.md` 2.5)
- **숨겨진 후기의 답글은 `MyReviewView`에서 떨어뜨린다.** B3는 자기 후기라면 `BLOCKED`도 보여 주므로 화면 조건만으로 두면 가려진 후기에 답글만 남는다

**검증**: 같은 후기에 두 번째 답글 거부, 비관리자 접근 거부와 CSRF, **B1 미리보기·B1 전체 목록·B3에 답글이 보이는지**, 답글이 **엉뚱한 후기에 붙지 않는지**, `BLOCKED`·`DELETED` 후기에 답글 작성·수정 거부, `BLOCKED` 후기의 답글이 고객 화면에서 함께 가려지는지, **답글 작성과 숨김의 동시 실행**(먼저 커밋된 숨김을 답글 저장이 보는지).
