# 조각 3 — 댓글

> **끝난 조각의 기록이다. 지금 구속하지 않는다.**
> 이 조각이 만든 규칙의 정본은 `../specs/community-comment.md`다.
> 조각 순서와 진행 상태, 위험과 결정 로그는 `../PLAN.md`.

- 대상 게시글이 `PUBLISHED`인지 검증 (DOMAIN.md 4.5)
- 삭제 시 자리 표시, 개수 집계에서 제외
- `parent_comment_id`를 코드에 등장시키지 않는다

**검증**: 삭제된 게시글에 댓글 작성 시도 거부, 삭제된 댓글이 개수에 안 세이는지, 남의 댓글 삭제 거부.

**완료 (2026-08-03)**. `CommunityMapper` +5 statement(`findRecentComments`·`countComments`·`findCommentById`·`insertComment`·`deleteComment`), `Comment` 엔티티(쓰기 경로 필드만), `dto/form/CommentForm`, `dto/view` 3종(`CommentView`·`CommentCountView`·`CommentSectionView`), `CommunityService` 5개 메서드, `CommunityController` 2개 핸들러 + 상세 확장, `detail.html`의 "댓글 기능은 준비 중입니다" 자리를 실제 댓글로 교체. migration은 없다 — `comments`에 필요한 컬럼이 V0에 전부 있다. `SecurityConfig`도 그대로다. 새 경로는 `anyRequest().authenticated()`에 걸린다.

검증은 `CommunityMapperTests`(+16), `CommunityMapperXmlTests`(+4), `CommunityServiceTests`(+19), `CommunityControllerTests`(+9), `CommunityScreenRenderingTests`(+10), `CommunityQueryCountTests`(+1), 새 `CommunityCommentScopeTests`(2)로 고정했다. 하네스 표에 H8·H9·H10·H11을 올렸다.

보류 항목이던 **댓글 페이징을 "최신 20건 + `이전 댓글 더 보기`"로 결정**했다(`../PLAN.md`의 결정 로그). DOMAIN.md 6.4에 정렬·분량·경로·상한을 함께 적었고 9절의 보류 줄을 지웠다.

구현 중 DOMAIN.md에 없던 빈칸 셋을 채우고 6.4에 반영했다: 댓글을 지울 수 있는 사람은 작성자 본인뿐이라는 것(게시글 작성자·관리자에게 권한이 없다), 댓글 작성뿐 아니라 **삭제에도** 게시글이 `PUBLISHED`여야 한다는 것, 삭제된 댓글의 본문을 조회 단계에서 `NULL`로 지운다는 것.

`getPostDetail`을 둘로 갈랐다. 댓글 검증이 실패해 상세를 다시 그릴 때 조회수를 올리면, **빈 댓글을 여러 번 보내는 것만으로 조회수가 오른다.** 화면에는 숫자가 커질 뿐이라 원인을 찾을 수 없다. 조회수를 올리는 `getPostDetail`과 올리지 않는 `getVisiblePost`로 나누고, 노출 판단은 `requireVisiblePost` 하나가 맡는다.

## 결정 로그에서 옮겨 온 리뷰 기록

`../PLAN.md` 결정 로그에는 한 줄만 남고 전문이 여기 있다.

| 날짜 | 내용 |
|---|---|
| 2026-08-04 | PR #93 Codex 리뷰 P2 3건 처리. (1) 게시글 상태 검증의 경합은 **R14와 같은 지적**이라 같은 근거로 수용하지 않았다. (2) **댓글 삭제 후 펼친 상태가 접히는 것은 고쳤다** — `screens/detail.md`가 "작성·삭제 후에는 `?comments=` 없이 돌아간다. 새 댓글은 언제나 최신 20건 안에 있으므로"라고 적고 있었는데, **그 근거는 작성에만 참이다.** 삭제에는 성공 메시지가 없어서 자리 표시가 결과를 보여 주는 유일한 신호인데, 접으면 최신 20건 밖의 자리 표시는 화면 밖으로 나가 삭제가 안 된 것과 구분되지 않는다. 삭제 폼이 지금 값을 실어 보내고 리다이렉트가 되돌린다. 검증 실패로 다시 그릴 때도 유지한다(제자리이므로). 받은 값은 정수로 다시 써서 주소에 넣는다. (3) **차단된 글의 댓글 삭제 버튼을 숨겼다** — 조건이 소유권만 보고 있어 작성자에게 눌러도 403만 나오는 죽은 버튼이 남았다. 댓글 폼과 같은 `canComment` 조건으로 묶었다. 둘 다 조건이 **두 벌로 갈라져 있던 자리**이고, DOMAIN.md 6.4가 삭제에 `PUBLISHED`를 요구한 이유가 바로 그것이었다 |
| 2026-08-04 | PR #91 Codex 리뷰 P2 2건을 **받아들이지 않기로** 결정하고 R14에 근거를 적었다. 지적 자체는 맞다 — 댓글 작성·삭제의 `PUBLISHED` 확인과 뒤따르는 쓰기 사이에 경합 창이 있다. 안 막는 이유는 셋이다. (1) **이건 불변식이 아니라 권한 판단이다.** 4.5가 "게시글을 지워도 자식 행은 그대로 둔다"이므로 "비노출 글에는 댓글이 없다"는 불변식이 애초에 없다. (2) **두 경우 다 손해 없는 쪽으로 틀린다** — 작성은 안 보이는 댓글 한 건, 삭제는 사용자가 자기 댓글을 뜻대로 지운 것이다. (3) **막는 값이 싸지 않다** — 댓글 쓰기마다 게시글 행에 잠금이 걸리는데 조각 6이 같은 행을 뜨겁게 만들었고, 우리는 바로 그 순서로 교착을 한 번 맞았다. 되돌아올 계기를 R8·R12와 같은 형식으로 적어 두었다. 리뷰를 보다 DOMAIN.md 6.4의 `더 보기` 조회수 항목이 조각 6 이후에도 미래형으로 남아 있는 것을 함께 발견해 고쳤다 |
