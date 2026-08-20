# 관리자 답글 — C5·C6·B4

> 공통 규칙(상태·평점·권한·검증·오류 코드·표시·도메인 경계)은 `../DOMAIN.md` 2절이 정본이다.
> 조각 순서와 진행 상태는 `../PLAN.md`. 결정의 배경은 `../decisions/`.
> 조각: 6

**답글은 작성(C5·C6)과 노출(B4)이 한 덩어리다.** 노출을 빼면 관리자는 답글을 쓰고 알림까지 나가는데 고객이 들어올 화면에 답글이 없다. 그래서 B4가 `review-read.md`가 아니라 여기 있다.

### C5. 답글 작성

**목업에는 답글 UI가 전혀 없었다.** 테이블과 알림 타입만 준비돼 있고 화면이 없어 조각 6에서 새로 만들었다.

| | |
|---|---|
| 액터 | 관리자 |
| 경로 | `POST /admin/reviews/{id:\d+}/replies` |
| 화면 | C3(`review-admin.md`) 상세 화면 안의 입력 영역 (조각 6에서 추가) |
| 입력 | `content` (1~1000자) |

**처리**

- **`PUBLISHED` 후기에만 답글을 단다.** `DELETED`는 `REVIEW_NOT_FOUND` 404, `BLOCKED`는 `BLOCKED_REVIEW` 403.
  - 관리자 목록·상세는 모든 상태를 보여 주므로(C1) 검증이 없으면 **숨긴 후기에도 답글이 저장된다.** 그리고 그 답글은 아무에게도 보이지 않는다 — `BLOCKED` 후기의 답글은 B4에서 함께 가려지고, `DELETED` 후기는 B3(`review-read.md`)에도 나오지 않는다.
  - 그 상태에서 D2(`review-notification.md`) 알림은 정상 발송된다. **고객은 알림을 받고 들어왔는데 볼 것이 없다.**
  - **상태 확인과 INSERT는 떨어져 있으면 안 된다.** 확인과 저장이 별개 문장이면 답글 작성과 C4 숨김이 동시에 들어올 때 숨김이 먼저 커밋돼도 답글 쪽은 먼저 읽은 `PUBLISHED`를 그대로 쓴다. FK는 후기의 **존재**만 보므로 `BLOCKED` 후기에 답글과 알림이 남는다. `DOMAIN 2.1`과 같은 모양으로 묶는다.
  - **`INSERT ... SELECT ... WHERE status = 'PUBLISHED'`로 조건을 저장 문장에 넣고 `affectedRows`로 판정한다.** REPEATABLE READ에서 `INSERT ... SELECT`의 원본 읽기는 잠금 읽기라 최신 커밋을 보므로, 트랜잭션 스냅샷이 아직 `PUBLISHED`여도 먼저 커밋된 숨김이 드러난다. C6의 `UPDATE`도 `reviews`를 조인해 같은 조건을 건다.
  - **0행일 때 원인은 최신 행을 다시 읽어 가린다**(`findByIdForUpdate`). 조각 4·5가 같은 자리를 같은 방법으로 처리한다.
- `review_replies`에 INSERT. `admin_id`는 **인증 관리자**에서 가져온다. `members(id)` FK이므로 사람이 다는 것을 전제한다.
- `uk_review_replies_review`가 후기당 1건을 강제한다. `DuplicateKeyException`을 잡아 `ALREADY_REPLIED` 409로 바꾼다.
- 저장 후 **D2(알림)를 호출** — `CUSTOMER_REVIEW`가 후기 작성자에게 간다. 조각 7에서 붙었고, 후기 트랜잭션이 커밋된 뒤에 나간다(`review-notification.md` D2).

### C6. 답글 수정

| | |
|---|---|
| 액터 | 관리자 |
| 경로 | `POST /admin/reviews/{id:\d+}/replies/edit` |
| 입력 | `content` |

**처리**

- `review_replies.content`만 바꾼다.
- **C5와 같이 `PUBLISHED` 후기에만 허용한다.** `DELETED`는 `REVIEW_NOT_FOUND` 404, `BLOCKED`는 `BLOCKED_REVIEW` 403이고, 조건은 C5처럼 저장 문장 안에 넣는다.
  - 근거: 답글에 손대는 조건을 작성과 수정에서 다르게 두면 "언제 답글을 만질 수 있는가"가 두 규칙이 된다. 숨긴 동안의 수정을 열어도 그 답글은 어차피 B4에서 가려져 아무에게도 보이지 않으므로, 고치려면 먼저 숨김을 해제한다.
- 답글이 없으면 `REPLY_NOT_FOUND` 404다.
- **수정에는 알림을 보내지 않는다.** 알림은 답글이 처음 달릴 때 한 번이다.

**답글 삭제는 열지 않는다.**

- 근거: `uk_review_replies_review`가 후기당 1건을 강제하므로 삭제 후 재작성은 수정과 결과가 같다. 삭제를 열면 "재작성 시 알림을 또 보내는가"를 판단해야 하고, `event_key` 설계(D2)가 그만큼 복잡해진다.
- 답글을 물리고 싶으면 내용을 고친다.

### B4. 답글 노출

- 후기당 답글은 최대 1개다(`uk_review_replies_review`).
- B1·B3(`review-read.md`)의 각 후기 아래에 접어 붙인다. 별도 화면을 만들지 않는다. B1 미리보기·전체 목록과 B3가 `fragments/customer/product-review.html`의 `reply` 프래그먼트 하나를 함께 쓴다.
- 표시: `사장님 답글` 라벨, 답글 본문, 작성일. **답글 작성자(관리자)의 실명은 표시하지 않는다.**
- 후기가 `BLOCKED`면 답글도 함께 가려진다. 판단 기준은 후기의 `status` 하나다.
