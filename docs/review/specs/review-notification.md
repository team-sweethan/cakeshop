# 알림 연동 — D2

> 공통 규칙(상태·평점·권한·검증·오류 코드·표시·도메인 경계)은 `../DOMAIN.md` 2절이 정본이다.
> 조각 순서와 진행 상태는 `../PLAN.md`. 결정의 배경은 `../decisions/`.
> 조각: 7

### D2. 알림

> **알림 규격은 `dev`에 있다.** PR #107이 #128로 머지되어 `NotificationType.NEW_REVIEW`·`CUSTOMER_REVIEW`, `notifications.review_id`·`review_reply_id`, `event_key` + `UNIQUE (receiver_id, event_key)`, `NotificationRequest`(`dto/form/`)가 전부 반영됐다. **더 이상 머지를 기다리는 의존이 아니다** — 조각 7이 마지막인 것은 순서상의 편의일 뿐이다(`PLAN.md` R1).

| 시점 | 타입 | 받는 사람 |
|---|---|---|
| A3(`review-write.md`) 후기 등록 | `NotificationType.NEW_REVIEW` | 관리자 |
| C5(`review-reply.md`) 답글 작성 | `NotificationType.CUSTOMER_REVIEW` | 후기 작성자 |

- `NotificationRequest`의 `reviewId`·`reviewReplyId`를 채운다.
- **`NEW_REVIEW`를 받을 관리자가 누구인지 아직 정해지지 않았다.** `NotificationRequest`는 `receiverId`를 필수로 받는데, 관리자 계정이 둘 이상이면 누구의 ID를 넣을지 정해야 구현할 수 있다. 활성 관리자 전원에게 각각인지 대표 계정 하나인지, 그 조회를 review·member·notification 중 어디가 맡는지가 함께 걸린다. 조각 7에서 민정님과 합의한다(`DOMAIN 4`).
  - `CUSTOMER_REVIEW`는 이 문제가 없다. 받는 사람이 후기 작성자 하나로 정해져 있다.
- `event_key`를 정한다. `uk_notifications_receiver_event` UNIQUE가 중복 알림을 막는다.
- `DeliveryScope`는 명시하지 않으면 서버가 `WEB_ONLY`로 채운다.
- **알림 실패가 후기 저장을 되돌리면 안 된다.** 후기는 저장됐는데 알림만 못 간 상태가 그 반대보다 낫다.
- A4·A5(`review-edit-delete.md`)·C4(`review-admin.md`)에는 알림을 보내지 않는다. C6(`review-reply.md`)도 보내지 않는다.
