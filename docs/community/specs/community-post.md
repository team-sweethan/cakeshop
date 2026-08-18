# 게시글 작성·수정·삭제 — A1·A2·A3·A4

> 공통 규칙(상태 모델·전이·권한·입력 검증)은 `../DOMAIN.md`가 정본이다.
> 조각 순서와 진행 상태는 `../PLAN.md`. 기능 ID와 spec 라우팅 표는 `../DOMAIN.md` 0.1절.
> 조각: 2

작성과 수정은 같은 `templates/customer/community/form.html`을 쓴다.

## A1. 게시글 작성 (`GET /community/new` · `POST /community`)

- 분류·제목·본문을 받는다. 길이와 trim 규칙은 `../DOMAIN.md` 7절이 정본이다.
- 카테고리 선택지에는 `is_active = 1`만 나온다(`../DOMAIN.md` 10절).

## A2. 게시글 수정 (`GET·POST /community/{id}/edit`)

- 수정 가능 항목: **제목, 본문, 카테고리** 전부.
- 수정 이력 테이블 없음. `updated_at != created_at`이면 화면에 "수정됨" 표시.
- 수정 가능 시간 제한 없음.
- **`updated_at`을 건드리지 않아야 하는 경로가 따로 있다.** 조회수 증가와 좋아요 재계산은 `updated_at`을 자기 값으로 다시 지정해 보존한다 — 안 하면 조회·좋아요만으로 화면에 `(수정됨)`이 붙는다. 근거는 `community-read.md` B3와 `community-reaction.md` A8에 있다.

## A3. 게시글 삭제 (`POST /community/{id}/delete`)

- 삭제는 `status = 'DELETED'` 전이다(`../DOMAIN.md` 4.2). 되돌릴 수 없고 휴지통은 MVP 밖이다.
- **차단된 글은 작성자도 수정·삭제할 수 없다.** `BLOCKED → DELETED`가 금지 전이라서다(`../DOMAIN.md` 4.2). 화면에서 버튼을 숨기는 것으로 끝내지 않고 Service가 거절한다.
- 자식 데이터(`comments`·`post_likes`·`post_reports`)는 그대로 둔다. 근거는 `../DOMAIN.md` 4.5.
- GET으로 지워지지 않게 폼 POST로 받는다. 링크 미리보기나 크롤러가 글을 없앨 수 있다.

## A4. 이미지 첨부 — 2차

**아직 아무것도 정해지지 않았다.** `post_images` 테이블은 V0에 있지만 1차에서는 쓰지 않는다.

착수 전에 정할 것: 업로드 저장 위치와 경로 규칙, 개수·용량 상한, 게시글 삭제·차단 시 이미지 처리,
본문이 순수 텍스트라는 규칙(`../DOMAIN.md` 7절)과의 관계 — 이미지가 본문 안에 들어가는지 첨부 목록으로
붙는지. **정해지지 않은 것을 정해진 것으로 가정해 구현하지 않는다.**

## 검증

이 spec이 소유하는 하네스다. 인덱스는 `../PLAN.md`에 있다.

**H2a — 소유권·상태 조건이 SQL에도 있음.** 남의 글·차단된 글은 UPDATE가 0행이다. `CommunityMapperTests`가 `updatePost`/`deletePost`에 남의 회원 번호와 `BLOCKED` 글을 넣고 갱신 행 수와 실제 값을 함께 본다. 화면과 Service만 막으면 조건이 두 벌로 갈라지고, 갈라진 순간부터 한쪽만 고치는 실수가 가능해진다.

**H2b — 차단된 글에 작성자가 아무 조치도 못 함.** 수정·삭제 모두 403이고 Mapper까지 내려가지 않는다. `CommunityPostServiceTests`가 버튼 숨김이 아니라 Service 거절을 본다. 화면 쪽은 `CommunityScreenRenderingTests.communityDetail_blockedPostAuthor_showsReasonAndHidesActions`가 맡는다.

**H2d — 조건부 UPDATE·DELETE가 0행이면 성공으로 넘어가지 않음.** 검증 통과 후 상태가 바뀐 순간을 잡는다. `CommunityPostServiceTests`가 갱신 행 수 0을 돌려주게 하고, 그 사이 `BLOCKED`가 되면 403이 나오는지까지 본다. **정상 흐름에서는 0행이 나오지 않으므로 이 검사가 없으면 드러나지 않는다** — 사용자에게는 성공 화면이 나오는데 글은 그대로다.
