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
- **`NEW_REVIEW`는 활성 관리자 전원에게 각각 보낸다**(2026-08-10 결정).
  - **팀 전제가 이미 "관리자 계정은 하나"다.** 채팅 스키마(PR #149, 아직 `dev` 미머지)가 `chat_rooms.admin_id`를 걷어내면서 `관리자 계정 하나가 전체 채팅방을 관리하므로`라고 적었고, `seed-local.sql`의 관리자 계정도 하나다. 채팅과 알림은 같은 담당이다.
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

**남은 것 — 알림에서 후기로 가는 링크.** `notifications.target_url`은 알림 migration이 걷어내고 타입별 ID로 옮겼고, 남아 있는 `NotificationRequest.targetUrl`은 저장되지 않는다. 링크는 `NotificationResponse.getTargetUrl()`이 ID로 되돌려 주는데 후기는 `reviewId != null → "/mypage"` 하나뿐이라, **관리자가 `NEW_REVIEW`에서 그 후기의 관리자 상세(C3)로 갈 수 없다.** 고치려면 그 fallback이 갈라져야 하는데 알림 도메인 파일이라 민정님과 합의가 필요하다.

**링크보다 앞선 것 — 알림 목록 화면 자체가 아직 없다.** `admin/notification/list.html`과 `customer/notification/list.html`은 둘 다 고정 목업이라 `/api/notifications`를 부르지 않는다. 목록·읽음 API는 이미 있고, 지금 살아 있는 표시는 공통 헤더의 미읽음 배지(`app.js`) 하나뿐이다. 관리자든 고객이든 사정이 같으므로 리뷰 알림만의 문제가 아니라 알림 도메인의 남은 화면 작업이다. 알림은 정상 저장되고 배지에 반영되므로 조각 7을 막지는 않는다.
