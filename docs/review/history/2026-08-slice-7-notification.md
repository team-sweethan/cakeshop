# 조각 7 — 알림 연동 (#114, PR #192 머지 완료)

> **끝난 조각의 기록이다. 지금 구속하지 않는다.**
> 이 조각이 만든 규칙의 정본은 `../specs/review-notification.md`.
> 조각 순서와 진행 상태는 `../PLAN.md`, 발견된 문제는 `../reviews/`,
> 방향을 고른 판단은 `../decisions/`에 있다.

**리뷰가 `NotificationService`의 첫 호출부다.** 도메인 밖에서 부르는 곳이 저장소에 하나도 없었다. 그래서 "관리자 알림을 누가 받는가"는 리뷰만의 문제가 아니라 `NEW_ORDER`·`ADMIN_CHAT`도 그대로 만날 자리다.

**새로 만든 계약 1건.** `MemberReviewQueryService.findActiveAdminIds()` — 조각 3에서 내가 만든 파일에 메서드를 더한 것이라 **수민님 파일은 그대로다.** 민정님 파일도 한 줄도 고치지 않았다 — `NotificationService.makeNotification`은 기존 공개 Service를 부르기만 한다.

- 작성 시 → `NotificationType.NEW_REVIEW` (**활성 관리자 전원에게 각각**)
- 답글 시 → `NotificationType.CUSTOMER_REVIEW` (작성자에게)
- `NotificationRequest`의 `reviewId`·`reviewReplyId`를 채운다
- `event_key`를 **명시한다** — 서버 기본값에 맡기면 그쪽 우선순위 사슬이 바뀔 때 같은 사건의 키가 달라진다
- `DeliveryScope`는 명시하지 않으면 서버가 `WEB_ONLY`로 채운다
- **커밋 이후에 `REQUIRES_NEW`로 보낸다.** 근거는 `../specs/review-notification.md` D2가 정본이다

**검증**: 활성 관리자 전원에게 가고 정지된 관리자는 빠지는지, 답글 알림이 작성자에게만 가는지, 같은 이벤트로 두 번 호출해도 알림이 하나인지, **알림 실패가 후기 저장을 되돌리지 않는지**, **후기가 rollback되면 알림도 없는지**, 수정·삭제·숨김·답글 수정에는 알림이 없는지.
