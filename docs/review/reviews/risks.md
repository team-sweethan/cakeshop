# Review 위험 등록부

> **파이프라인의 `문제 발견` 자리다.** 발견됐지만 아직 규칙으로도 코드로도 닫히지 않은 것,
> 그리고 **알면서 감수하기로 한 것**이 여기 남는다. 해소된 위험은 지우지 않고 취소선으로 둔다.
>
> **번호는 `../PLAN.md`의 위험 인덱스가 갖는다.** `../specs/*.md`가 `../PLAN.md R4`처럼 번호로 들어온다.
>
> 해결 방향을 고른 것은 `../decisions/`, 지금 지켜야 하는 규칙은 `../specs/`,
> 끝난 조각의 기록은 `../history/`에 있다.

| # | 위험 | 상태 |
|---|---|---|
| R1 | ~~알림 규격이 `dev`에 없다~~ — **해소 (2026-08-07).** PR #107이 #128로 머지되어 `NotificationType.NEW_REVIEW`·`CUSTOMER_REVIEW`, `notifications.review_id`·`review_reply_id`, `event_key` + `uk_notifications_receiver_event`, `NotificationRequest`(`dto/form/`)가 전부 `dev`에 있다. 직접 확인함 | 해소 |
| R2 | ~~**`products.average_rating`·`review_count`는 이미 상품 정렬에 쓰이는데 갱신되지 않는다.**~~ — **해소.** 조각 1·2를 한 배포 단위로 묶어 PR #177·#178을 함께 머지했다. 조각 1 단독 머지를 막은 것이 이 위험의 대응 그 자체였다 | 해소 |
| R3 | **`reviews.status` 기본값 `'VISIBLE'`은 코드베이스 어디에도 없는 어휘였다** | **해소** — 조각 0(PR #125). 보정 UPDATE 후 `chk_reviews_status`, 기본값도 `'PUBLISHED'` (`../DOMAIN.md` 2.1) |
| R4 | **`reviews.product_id`가 `order_items.product_id`와 어긋날 수 있다.** 요청값을 믿으면 남의 상품에 후기를 붙일 수 있다 | 조각 1에서 `order_items`로부터 파생. 테스트로 고정 (`../specs/review-write.md` A3) |
| R5 | **평점 범위 제약이 DB에 없었다.** `TINYINT UNSIGNED`라 0과 255가 들어갔다 | **해소** — 조각 0(PR #125). 평점 4종에 제약을 하나씩. 화면·서버를 합쳐 세 겹 (`../DOMAIN.md` 2.2) |
| R6 | **목업의 평점 4종이 스키마와 어긋난다.** 목업 `맛`/`디자인`/`포장`/`응대` vs 스키마 `overall`/`taste`/`design`/`service` | **해소** — 2026-08-05에 스키마를 택하고 `포장`을 `응대`로 흡수, 화면에 `전체` 추가 (`../DOMAIN.md` 2.2) |
| R7 | ~~PR #107의 migration이 V0의 `notifications`·`notification_deliveries`를 `DROP` 후 재생성한다~~ — **해소 (2026-08-07).** 실제로 머지된 `V20260804_150010__add_notification_tables.sql`은 테이블을 재생성하지 않고 제약·컬럼을 `ALTER`한다. 이미 `dev`에 있으므로 머지 순서 문제도 없다. 직접 확인함 | 해소 |
| R8 | **상품별 후기 조회를 받쳐 줄 인덱스가 `fk_reviews_product`뿐이라 정렬에 filesort가 붙는다.** 조각 3이 그 쿼리를 실제로 냈으므로 **이제 가정이 아니라 지금 도는 경로다.** 1차 데이터로는 문제가 없고 온라인 DDL로 나중에 붙일 수 있다 | 1차에선 수용. **되돌아올 계기**: 한 상품의 후기가 수백 건을 넘거나 상품 상세가 눈에 띄게 느려지면 `(product_id, status, created_at, id)` 복합 인덱스를 새 migration으로 |
| R9 | ~~**조각 2는 상품 도메인(시은 담당)에 파일을 만든다.**~~ — **해소.** `ProductReviewCommandService.lockForRating`·`applyReviewAggregate`로 확정해 PR #178로 머지됐다. 시은님 파일은 한 줄도 고치지 않았다 | 해소 |
| R10 | **후기를 삭제하면 그 주문 상품에는 다시 쓸 수 없다.** soft delete라 행이 남고 `uk_reviews_order_item`이 재작성을 막는다 | **대응 완료.** 조각 4의 삭제 버튼에 확인창 문구가 붙었고(`customer/review/my.html`), A1은 조건 없는 `NOT EXISTS`로 걸러 목록에 띄워 놓고 저장에서 거절하는 일을 막는다 (`../specs/review-write.md`, `../specs/review-edit-delete.md`) |
| R11 | **`RDS`에는 migration이 자동 적용되지 않는다.** `rds` 프로파일이 `flyway.enabled: false`라 승인된 별도 절차로만 반영된다. 조각 1~3은 `status` 기본값 `PUBLISHED`와 `CHECK`를 전제한다 | **미해소.** 조각 1·2·3은 로컬·CI(Testcontainers)에서만 확인했고 **RDS 반영 여부는 아직 모른다.** `application.yml` 주석(2026-08-05)은 Flyway가 꺼져 있어 테이블 자체가 만들어지지 않았다고 적고 있다. 사실이라면 착수 관문이 아니라 **최초 배포 때 한꺼번에 처리할 일**이다 (2026-08-10 개정) |
| R12 | **관리자 검색이 계약에서 받는 ID 목록에 상한이 없다.** 닉네임 한 글자가 수천 명을 맞히면 `IN` 절이 그만큼 길어진다 | **1차에선 수용.** 상한을 두면 목록과 전체 건수가 갈리는데 그게 C2가 애초에 막으려던 것이다. **되돌아올 계기**: 회원이나 주문 상품이 수천 건을 넘고 관리자 검색이 눈에 띄게 느려지면, 계약을 ID 목록이 아니라 **페이지 결과**를 돌려주는 모양으로 바꾼다 |
