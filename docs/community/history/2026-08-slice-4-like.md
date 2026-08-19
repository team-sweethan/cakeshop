# 조각 4 — 좋아요

> **끝난 조각의 기록이다. 지금 구속하지 않는다.**
> 이 조각이 만든 규칙의 정본은 `../specs/community-reaction.md`다.
> 조각 순서와 진행 상태는 `../PLAN.md`, 발견된 문제는 `../reviews/`,
> 방향을 고른 판단은 `../decisions/`에 있다.

- POST/DELETE 분리, 둘 다 멱등
- `like_count` 재계산

**검증**: 같은 요청 반복 시 카운트 불변, **동시 요청 후 `like_count == post_likes 실제 개수`**, 삭제된 게시글에 좋아요 시도 거부.

**완료 (2026-08-04)**. `CommunityMapper` +5 statement(`lockPost`·`insertLike`·`deleteLike`·`recalculateLikeCount`·`existsLike`), `dto/view/PostLockView`, `CommunityService` 3개 메서드 + `requireLikeablePost`, `CommunityController` 2개 핸들러 + 상세 모델 확장(`canLike`·`likedByViewer`), `detail.html`의 숫자만 있던 `좋아요 N` 자리를 버튼과 함께 교체. migration은 없다 — `post_likes`(`UNIQUE(post_id, member_id)` + FK 2개)와 `posts.like_count`가 V0에 전부 있다. `SecurityConfig`도 그대로다. 공개 규칙이 `GET` 한정이라 새 POST 경로는 `anyRequest().authenticated()`에 걸린다.

검증은 `CommunityMapperTests`(+7), `CommunityMapperXmlTests`(+3), `CommunityServiceTests`(+9), `CommunityControllerTests`(+8), `CommunityScreenRenderingTests`(+5), 새 `CommunityLikeConcurrencyTests`(3)로 고정했다. 하네스 표에 H2c(예정이던 것)와 H15를 올렸다.

**교착이 조각 6과 같은 자리에서 다시 나왔고, 이번에는 미리 막았다.** `post_likes` INSERT가 FK 확인으로 부모 `posts` 행에 공유 잠금을 걸고 뒤따르는 재계산이 배타 잠금을 기다리는, H13과 똑같은 모양이다. **다만 해법은 쓸 수 없었다** — 조회수는 순서를 뒤집어 풀었는데, 여기서는 재계산이 INSERT 이후여야 새 행을 세므로 뒤집을 데가 없다. 그래서 `SELECT ... FOR UPDATE`로 배타 잠금을 앞에서 잡아 요청들이 한 줄로 서게 했다. **같은 종류의 함정이 두 번째로 나왔다는 것이 이 조각에서 배운 것이다** — `posts` 행에 쓰는 경로가 늘 때마다 잠금 순서를 따져야 한다.

**하네스가 무는지 직접 확인했다.** `FOR UPDATE`를 떼고 돌려 보니 `CommunityLikeConcurrencyTests` 세 개가 전부 `DeadlockLoserDataAccessException`으로 실패했고, 형태 검사도 함께 물었다. 교착은 추측이 아니라 **실제로 나는 것**이었다. 조각 1에서 배운 대로 — "검사가 있다"와 "검사가 문다"는 다르다.

잠금 조회를 `findPostById`로 재사용하지 않은 이유가 하나 더 있다. **`FOR UPDATE`는 조인한 테이블의 행까지 잠근다.** 상세 조회는 `post_categories`·`members`를 조인하므로 그대로 썼다면 좋아요 한 번에 카테고리 행이 잠기고 **같은 분류의 모든 글이 서로 줄을 선다.** 교착처럼 터지지 않고 조용히 느려지기만 해서 더 찾기 어려운 종류다. 그래서 `posts`만 읽는 `lockPost`와 `PostLockView`를 따로 뒀다.

**이 잠금 덕분에 좋아요 경로에는 R14의 경합 창이 없다.** 댓글에서 같은 잠금을 마다한 근거가 "막는 값이 싸지 않다"였는데, 좋아요는 `like_count` 때문에 어차피 같은 행에 배타 잠금을 잡아야 해서 값이 0이다. R14는 댓글에 대해서만 유효하다.
