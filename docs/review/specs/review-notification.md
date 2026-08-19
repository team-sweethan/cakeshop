# 알림 연동 — D2·D3

> 공통 규칙(상태·평점·권한·검증·오류 코드·표시·도메인 경계)은 `../DOMAIN.md` 2절이 정본이다.
> 조각 순서와 진행 상태는 `../PLAN.md`. 결정의 배경은 `../decisions/`.
> 조각: 7(D2 발송) · 9(D3 링크)

### D2. 알림

> **알림 규격은 `dev`에 있다.** PR #107이 #128로 머지되어 `NotificationType.NEW_REVIEW`·`CUSTOMER_REVIEW`, `notifications.review_id`·`review_reply_id`, `event_key` + `UNIQUE (receiver_id, event_key)`, `NotificationRequest`(`dto/form/`)가 전부 반영됐다. **더 이상 머지를 기다리는 의존이 아니다** — 조각 7이 마지막인 것은 순서상의 편의일 뿐이다(`../PLAN.md` R1).

| 시점 | 타입 | 받는 사람 |
|---|---|---|
| A3(`review-write.md`) 후기 등록 | `NotificationType.NEW_REVIEW` | 관리자 |
| C5(`review-reply.md`) 답글 작성 | `NotificationType.CUSTOMER_REVIEW` | 후기 작성자 |

- `NotificationRequest`의 `reviewId`·`reviewReplyId`를 채운다.
- **`NEW_REVIEW`는 활성 관리자 전원에게 각각 보낸다**(2026-08-10 결정).
  - **팀 전제가 이미 "관리자 계정은 하나"다.** 채팅 스키마(PR #149, 머지 완료)가 `chat_rooms.admin_id`를 걷어내면서(`V20260807_112117__align_chat_schema.sql`) `관리자 계정 하나가 전체 채팅방을 관리하므로`라고 적었고, `seed-local.sql`의 관리자 계정도 하나다. 채팅과 알림은 같은 담당이다.
  - **2026-08-10에 담당자가 직접 확인해 주었다** — 알림·채팅은 관리자 계정 하나를 돌려쓰는 전제로 구현 중이다. 코드에서 읽어 낸 전제가 아니라 합의된 사실이다.
  - 그 전제 아래서는 "전원"이 곧 그 하나라 결과가 같고, **관리자가 둘이 되어도 깨지지 않는다.** 전제를 코드에 박지 않는 쪽을 골랐다.
  - 대표 계정 하나를 고르는 안은 `members`에 그것을 가리키는 컬럼이 없어 스키마를 바꿔야 한다. 담당자가 `admin_id`를 오히려 걷어낸 방향과도 어긋난다.
  - **관리자 조회는 member 도메인이 맡는다** — 관리자 판별은 `role`·`status`라는 회원 도메인의 사실이다. 계약은 `MemberReviewQueryService.findActiveAdminIds()` → `List<Long>`이고 `role = 'ADMIN' AND status = 'ACTIVE'`로 거른다. 탈퇴·정지된 관리자를 넣으면 받을 사람이 없는 알림이 쌓이고, 계정을 되살리면 그동안의 알림이 한꺼번에 보인다.
  - `CUSTOMER_REVIEW`는 이 문제가 없다. 받는 사람이 후기 작성자 하나로 정해져 있다.
- **`event_key`는 명시한다.** `NEW_REVIEW:{adminId}:{reviewId}`, `CUSTOMER_REVIEW:{authorId}:{reviewReplyId}`. `uk_notifications_receiver_event` UNIQUE가 중복 알림을 막는다.
  - 서버가 채워 주는 기본 키와 값은 같지만 맡기지 않는다. 기본값은 "가장 세밀한 연관 ID"를 고르는 우선순위 사슬에서 나오므로, 그 사슬이 바뀌면 같은 사건의 키가 조용히 달라져 UNIQUE가 막던 중복이 되살아난다.
- `DeliveryScope`는 명시하지 않으면 서버가 `WEB_ONLY`로 채운다.
- **알림 실패가 후기 저장을 되돌리면 안 된다.** 후기는 저장됐는데 알림만 못 간 상태가 그 반대보다 낫다.
  - **그래서 커밋 이후에 보낸다.** 후기 트랜잭션 안에서 부르면 알림 쪽 예외가 그 트랜잭션을 rollback-only로 만들어, 잡아도 후기가 함께 사라진다. `TransactionSynchronization.afterCommit`으로 미루고 실패는 로그만 남긴다(`ReviewNotificationService`).
  - **발송은 `REQUIRES_NEW`여야 한다.** `afterCommit` 시점에는 완료 중인 트랜잭션이 아직 살아 있어, 기본 전파로 부르면 그 트랜잭션에 얹혀 아무도 커밋하지 않고 사라진다(`ReviewNotificationSender`). 알림 도메인의 `NotificationDeliveryService`가 같은 이유로 같은 모양이다.
  - 반대로 커밋 전에 `REQUIRES_NEW`로 먼저 보내는 안은 택하지 않았다. 후기가 커밋에 실패하면 **없는 후기의 알림**이 남는데, 그게 위에서 더 나쁘다고 한 쪽이다.
  - **치르는 대가 — 발송하는 동안 요청 하나가 커넥션을 둘 잡는다.** `afterCommit`은 원 트랜잭션이 커넥션을 반납하기 전에 돈다(`cleanupAfterCompletion`이 그 뒤다). 동시 쓰기가 풀 크기에 근접하면 발송이 대기하다 타임아웃으로 실패할 수 있고, 풀은 `application.yml`에 사이징이 없어 Hikari 기본값 10이다. 그때도 후기는 이미 커밋된 뒤라 남고 알림만 접힌다 — 위에서 고른 쪽 그대로다. 겹치는 창은 짧다. 이 경로는 `WEB_ONLY`라 관리자 조회 `SELECT` 하나와 `INSERT`로 끝나고 외부 호출이 없다.
  - 이 대가를 없애려면 발송을 큐로 빼야 하는데 **후기만 바꿔서는 줄지 않는다.** 알림 도메인의 `NotificationService.registerSmsSending`이 같은 `afterCommit` 자리에서 `REQUIRES_NEW`로 외부 발송까지 하고 있어 커넥션을 더 오래 잡는다. 알림 발송 구조를 옮길 때 함께 정리할 항목이다.
- A4·A5(`review-edit-delete.md`)·C4(`review-admin.md`)에는 알림을 보내지 않는다. C6(`review-reply.md`)도 보내지 않는다.

### D3. 알림에서 후기로 가는 링크

`notifications.target_url`은 알림 migration이 걷어내고 타입별 ID로 옮겼고, 남아 있는 `NotificationRequest.targetUrl`은 저장되지 않는다. 링크는 `NotificationResponse.getTargetUrl()`이 ID로 되돌려 준다. 조각 7이 남긴 `reviewId != null → "/mypage"` 하나로는 **관리자도 작성자도 어느 후기인지 알 수 없다.** 목표는 **후기 두 타입 모두 목록이 아니라 그 후기를 여는 것**이다.

> **확정됐다 (2026-08-19, PR #329).** 아래 링크 두 줄은 민정님이 쓴 `NotificationResponse.getTargetUrl()`을 고쳐야 하는 일이었고, 그 변경이 머지되어 지금 동작한다. 착수 당시의 합의 경위는 `../history/2026-08-slice-9-notification-deeplink.md`에 있다.

| 타입 | 받는 사람 | 링크 |
|---|---|---|
| `NEW_REVIEW` | 관리자 | `/admin/reviews/{reviewId}` — C3 관리자 상세 |
| `CUSTOMER_REVIEW` | 후기 작성자 | `/mypage/reviews/{reviewId}#review-{reviewId}` — B3 내 후기 목록의 그 자리 |

- **고객 쪽은 단건 화면을 새로 만들지 않고 커뮤니티 댓글 deep link와 같은 모양을 쓴다.** `GET /mypage/reviews/{reviewId}`가 내 후기 목록을 그대로 렌더링하되, 대상 후기가 첫 쪽 밖이면 `ReviewService.getFocusedMyReviews`가 가장 오래된 한 건을 밀어내고 창 안에 넣는다. 쪽 크기를 늘리지 않으면서 목적지가 반드시 화면에 남는다. 강조는 `review.css`의 `:target`이고 JavaScript를 쓰지 않는다.
- **대상은 본인의 `DELETED`가 아닌 후기여야 한다.** 남의 후기와 없는 후기를 `REVIEW_NOT_FOUND` 하나로 묶는 것은 2.5의 이유와 같다. `BLOCKED`는 막지 않는다 — B3이 숨겨진 후기를 작성자에게는 보여 주므로 여기서 더 좁히면 목록과 어긋난다.
- **`/mypage/reviews/writable`(A1)과 겹치지 않게 경로 변수는 `{reviewId:\d+}`로 숫자만 받는다.**
- **두 링크는 `NotificationResponse.getTargetUrl()`이 파생시킨다** — 민정님이 쓴 코드이고, 조각 9(PR #329)에서 고쳐 머지했다.
  - **후기 쪽에서 우회할 방법은 없었다.** `getTargetUrl()`은 `targetUrl` 필드가 있으면 그것을 먼저 돌려주지만, `NotificationRequest.targetUrl`을 읽는 곳이 없고 `notifications.target_url` 컬럼은 `V20260804_150010`이 걷었다. 발송 쪽에서 링크를 실어 보낼 길이 없어 파생 메서드를 고치는 것이 유일한 경로였다.
  - **담당자가 쓴 코드를 고치는 일이라 `docs/conventions.md` 12절의 선행 합의가 걸렸다.** 새 계약을 만드는 경우(협의 없이 만들고 리뷰어 지정으로 확인)와 갈리는 자리다. 착수 당시의 경위는 `../history/2026-08-slice-9-notification-deeplink.md`에 있다.
