# 조각 3 — 댓글

> **끝난 조각의 기록이다. 지금 구속하지 않는다.**
> 이 조각이 만든 규칙의 정본은 `../specs/community-comment.md`다.
> 조각 순서와 진행 상태는 `../PLAN.md`, 발견된 문제는 `../reviews/`,
> 방향을 고른 판단은 `../decisions/`에 있다.

- 대상 게시글이 `PUBLISHED`인지 검증 (DOMAIN.md 4.5)
- 삭제 시 자리 표시, 개수 집계에서 제외
- `parent_comment_id`를 코드에 등장시키지 않는다

**검증**: 삭제된 게시글에 댓글 작성 시도 거부, 삭제된 댓글이 개수에 안 세이는지, 남의 댓글 삭제 거부.

**완료 (2026-08-03)**. `CommunityMapper` +5 statement(`findRecentComments`·`countComments`·`findCommentById`·`insertComment`·`deleteComment`), `Comment` 엔티티(쓰기 경로 필드만), `dto/form/CommentForm`, `dto/view` 3종(`CommentView`·`CommentCountView`·`CommentSectionView`), `CommunityService` 5개 메서드, `CommunityController` 2개 핸들러 + 상세 확장, `detail.html`의 "댓글 기능은 준비 중입니다" 자리를 실제 댓글로 교체. migration은 없다 — `comments`에 필요한 컬럼이 V0에 전부 있다. `SecurityConfig`도 그대로다. 새 경로는 `anyRequest().authenticated()`에 걸린다.

검증은 `CommunityMapperTests`(+16), `CommunityMapperXmlTests`(+4), `CommunityServiceTests`(+19), `CommunityControllerTests`(+9), `CommunityScreenRenderingTests`(+10), `CommunityQueryCountTests`(+1), 새 `CommunityCommentScopeTests`(2)로 고정했다. 하네스 표에 H8·H9·H10·H11을 올렸다.

보류 항목이던 **댓글 페이징을 "최신 20건 + `이전 댓글 더 보기`"로 결정**했다(`../decisions/decision-log-1st.md`). DOMAIN.md 6.4에 정렬·분량·경로·상한을 함께 적었고 9절의 보류 줄을 지웠다.

구현 중 DOMAIN.md에 없던 빈칸 셋을 채우고 6.4에 반영했다: 댓글을 지울 수 있는 사람은 작성자 본인뿐이라는 것(게시글 작성자·관리자에게 권한이 없다), 댓글 작성뿐 아니라 **삭제에도** 게시글이 `PUBLISHED`여야 한다는 것, 삭제된 댓글의 본문을 조회 단계에서 `NULL`로 지운다는 것.

`getPostDetail`을 둘로 갈랐다. 댓글 검증이 실패해 상세를 다시 그릴 때 조회수를 올리면, **빈 댓글을 여러 번 보내는 것만으로 조회수가 오른다.** 화면에는 숫자가 커질 뿐이라 원인을 찾을 수 없다. 조회수를 올리는 `getPostDetail`과 올리지 않는 `getVisiblePost`로 나누고, 노출 판단은 `requireVisiblePost` 하나가 맡는다.
