# 조각 6 — 조회수 중복 방지

> **끝난 조각의 기록이다. 지금 구속하지 않는다.**
> 이 조각이 만든 규칙의 정본은 `../specs/community-read.md` B3다.
> 조각 순서와 진행 상태, 위험과 결정 로그는 `../PLAN.md`.

**왜 이것이 먼저인가**: 조각 7의 순위가 전부 이 값에 기댄다. 순서를 바꾸면 **조작이 되는 순위 화면**을 먼저 공개하게 되고, 그 사이 쌓인 `view_count`는 나중에 신뢰할 수 없어 어차피 다시 세야 한다.

- 새 migration: `post_views(post_id, viewer_key, viewed_on)` + `UNIQUE (post_id, viewer_key, viewed_on)` + `posts(id)` FK — **당시 계획이다. 2026-08-04에 10분 창으로 바뀌면서 `viewed_on`과 `UNIQUE`가 없어졌다**(DOMAIN.md 6.2가 정본)
- `viewer_key`는 회원이면 `M:{memberId}`, 비로그인이면 `S:{sessionId}` (DOMAIN.md 6.2)
- 상세 조회 흐름을 `INSERT 시도 → 삽입 1행일 때만 view_count +1 → SELECT 상세`로 바꾼다
- migration 파일명은 직접 짓지 않고 `gradlew newMigration -Pdesc=<snake_case>`로 생성

**검증**:
- 같은 회원이 같은 날 같은 글을 여러 번 열어도 `view_count`가 1만 오르는지
- 날짜가 바뀌면 다시 오르는지
- **동시 요청 후 `view_count == post_views 실제 개수`** (H2c의 좋아요판과 같은 형태)
- 노출되지 않는 글은 `post_views` 행도 남기지 않는지 — 지금은 `view_count`만 안 오른다
- 비로그인 세션이 유지되는 동안 재조회가 안 세이는지
- 조각 3의 `더 보기`를 눌러도 조회수가 안 오르는지 (지금은 클릭마다 오른다 — R13)

**함께 손봐야 하는 것**: `CommunityQueryCountTests.getPostDetail_queryCount_isFixed`가 상세 SELECT 1회를 단언한다. `INSERT`는 update 경로라 세지 않지만, 중복 여부를 SELECT로 확인하는 구현으로 가면 이 수가 바뀐다 — **DB가 판단하게 두면 안 바뀐다.**

**완료 (2026-08-04)**. `V20260803_235726__add_post_views.sql`, `CommunityMapper.recordView` 및 `increaseViewCount` 재작성, `CommunityService.getPostDetail`에 `viewerKey` 추가, `CommunityController.viewerKeyOf`, `seed-community.sql`에 조회 이력 절 추가. 하네스 표에 H12·H13·H14를 올렸다.

검증은 `CommunityMapperTests`(+8), `CommunityMapperXmlTests`(+2), `CommunityServiceTests`(+3), `CommunityControllerTests`(+2), 새 `CommunityViewCountTests`(6)·`CommunityViewCountConcurrencyTests`(2)로 고정했다.

**구현 중 교착 상태를 발견해 설계를 바꿨다.** 처음에는 "이력을 먼저 넣고, 새로 들어갔으면 숫자를 올린다"로 만들었는데, `post_views` INSERT가 FK 확인 때문에 부모인 `posts` 행에 **공유 잠금(S)** 을 걸고 그 다음 조회수 UPDATE가 같은 행의 **배타 잠금(X)** 을 기다린다. 같은 글을 동시에 연 요청 둘이 서로 S를 쥔 채 상대의 X를 기다리면 그대로 교착이다. 인기 있는 글일수록 더 잘 터지는데, **단일 스레드 테스트로는 절대 드러나지 않는다.** `CommunityViewCountConcurrencyTests`를 만들고 나서야 잡혔다.

그래서 순서를 뒤집어 조회수 UPDATE가 X를 먼저 잡고 `NOT EXISTS`로 중복까지 판단하게 했다. 이력 INSERT는 이미 X를 쥔 상태에서 하므로 잠금이 뒤집히지 않는다. 이 순서를 H13이 고정한다.

**`ON DUPLICATE KEY UPDATE`의 갱신 행 수를 믿을 수 없다는 것도 여기서 드러났다.** MariaDB JDBC 드라이버가 `CLIENT_FOUND_ROWS`를 켜서 갱신 행 수가 '바뀐 행'이 아니라 '찾은 행'을 뜻한다. "값이 그대로면 0"에 기대는 방식은 여기서 언제나 1을 돌려주고, **제약은 멀쩡히 도는데 숫자만 부푼다.** 이력과 숫자를 비교해 보기 전에는 드러나지 않는다.

시드에서 **기존 버그도 하나 고쳤다.** `like_count` 재계산 UPDATE가 `updated_at`을 보존하지 않아, 좋아요를 받은 글마다 화면에 `(수정됨)`이 붙어 있었다. 6.2·6.3이 경고하는 바로 그 유형이고 같은 파일이라 함께 고쳤다.

## 결정 로그에서 옮겨 온 리뷰 기록

`../PLAN.md` 결정 로그에는 한 줄만 남고 전문이 여기 있다.

| 날짜 | 내용 |
|---|---|
| 2026-08-04 | PR #98 Codex 리뷰 3건 전부 처리. (1) **댓글 `더 보기`를 조회수 경로에서 뺐다(P2, 셋 중 제일 무겁다)** — 지적이 맞다. `?comments=`가 붙은 요청도 조회수를 올리는 `getPostDetail`로 가고 있어서, 상세를 **10분 넘게 읽다가** 누르면 창이 이미 닫혀 그대로 +1이었다. 날짜 칸일 때는 하루 한 번으로 눌려 있어 드러나지 않던 것이 창을 좁히면서 되살아났다. **문제는 구멍보다 문서와 하네스가 그걸 아니라고 말하고 있었다는 것이다** — H12가 "댓글 `더 보기` 전부"라고 적고 있었는데, 그 행의 테스트들은 창을 한 번도 넘지 않아 이 경계를 통째로 놓쳤다. 조각 1에서 배운 그 성질이 또 나왔다: 빈 곳은 실패가 아니라 통과의 모습으로 나타난다. 조각 3이 갈라 둔 `getVisiblePost`를 그대로 쓰고, H12에서 이 자리를 떼어 H20으로 따로 세웠다 — 창이 지키는 것과 경로가 지키는 것이 섞여 있으면 어느 쪽이 무는지 알 수 없다. (2) **세 `ALTER`를 한 문장으로 묶었다(P2)** — MariaDB DDL은 트랜잭션이 아니라 Flyway가 통째로 롤백해 주지 않는다. 첫 문장이 커밋된 뒤 둘째가 잠금 시간 초과로 실패하면 인덱스만 남고 버전은 기록되지 않아, 재시도가 매번 `Duplicate key name`으로 죽는다 — 손으로 지우기 전에는 복구되지 않는다. **migration 주석이 "통째로 실패한다"고 적고 있던 것이 틀린 자리였다.** (3) **expand-contract로 나눴다(P1)** — 지적의 전제(공용 RDS 무중단 롤링)는 지금 성립하지 않지만(R21), 나누는 값이 컸다. `viewed_on`을 지우지 않고 `NULL` 허용으로만 바꾸면 구버전(날짜를 넣는다)과 신버전(넣지 않는다)이 **같은 스키마에서 함께 돈다.** 컬럼 삭제는 R20에 계기와 함께 남겼다. 스키마 검사 3건을 새로 넣었고, 그중 "`viewed_on` 없이 INSERT된다"는 **컬럼을 지운 뒤에도 그대로 참**이라 다음 단계에서 고칠 테스트가 없다 |
