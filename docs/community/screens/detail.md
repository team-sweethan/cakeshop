# 상세 — `GET /community/{postId}`

> 이 파일의 형식과 검사 규칙은 `docs/community/SCREENS.md`에 있다.

- 상태: 구현됨 (조각 1)
- 템플릿: `customer/community/detail.html`
- 핸들러: `CommunityController.detail`
- 접근: 공개. 단 노출 여부는 글 상태에 따라 갈린다 (DOMAIN.md 4.3)

```
┌─────────────────────────────────────────────────────┐
│ [공통 헤더 fragment]                                 │
├─────────────────────────────────────────────────────┤
│  [ ← 목록 ]                                          │
│                                                     │
│  ┌───────────────────────────────────────────────┐  │
│  │ 관리자가 차단한 게시글입니다.                  │  │  ← BLOCKED + 작성자 본인
│  │ 광고성 게시물                                  │  │
│  │ 이 글은 다른 회원에게 보이지 않습니다.         │  │
│  └───────────────────────────────────────────────┘  │
│                                                     │
│  [질문]                                             │
│  딸기 케이크 보관법                                  │
│  케이크덕후 · 2026.03.01 · 조회 42 (수정됨)          │
│  ───────────────────────────────────────────────    │
│  본문. 줄바꿈은 그대로 살아난다.                     │
│                                                     │
│  좋아요 3  [ 좋아요 ]                                │  ← 로그인 + PUBLISHED
│  ───────────────────────────────────────────────    │
│  댓글 2                                              │
│  [ 이전 댓글 더 보기 ]  남은 댓글 5   ← 댓글 21건부터 │
│  ┌───────────────────────────────────────────────┐  │
│  │ 삭제된 댓글입니다.                             │  │  ← 지워진 자리
│  ├───────────────────────────────────────────────┤  │
│  │ 케이크덕후 · 2026.03.01        [ 댓글 삭제 ]   │  │  ← 자기 댓글에만
│  │ 저도 궁금했어요.                               │  │
│  └───────────────────────────────────────────────┘  │
│  댓글 작성                                           │
│  ┌───────────────────────────────────────────────┐  │  ← 로그인 회원에게만
│  │                                               │  │
│  └───────────────────────────────────────────────┘  │
│  등록한 댓글은 수정할 수 없습니다.   [ 댓글 등록 ]    │
├─────────────────────────────────────────────────────┤
│ [공통 푸터 fragment]                                 │
└─────────────────────────────────────────────────────┘
```

## 모델

| 이름 | 타입 | 내용 |
|---|---|---|
| `post` | `PostDetailView` | 글 본문과 상태. `blocked`, `edited`, `authorName()`은 뷰가 계산한다 |
| `canEdit` | `boolean` | 수정·삭제 버튼을 보여줄지. 작성자 본인이고 차단되지 않은 글일 때만 참 |
| `canComment` | `boolean` | 댓글 폼을 보여줄지. 로그인했고 차단되지 않은 글일 때만 참 |
| `canLike` | `boolean` | 좋아요 버튼을 보여줄지. `canComment`와 **같은 값**이다 — DOMAIN.md 4.5가 댓글·좋아요에 같은 규칙을 준다. 이름이 둘인 것은 화면의 구역이 둘이기 때문이다 |
| `likedByViewer` | `boolean` | 버튼이 `좋아요`인지 `좋아요 취소`인지. 누를 수 없는 상대에게는 묻지 않으므로 비로그인은 언제나 거짓 |
| `viewerId` | `Long` | 보고 있는 회원. 자기 댓글에만 삭제 버튼을 다는 데 쓴다. 비로그인이면 `null` |
| `commentSection` | `CommentSectionView` | 댓글 목록과 두 개수. `canLoadMore()`, `cappedByLimit()`, `hiddenCount()`는 뷰가 계산한다 |
| `commentForm` | `CommentForm` | 댓글 입력값. 검증 실패 시 입력을 담은 채 이 화면이 다시 그려진다 |
| `canReport` | `boolean` | 신고 폼을 보여줄지. 로그인했고, 차단되지 않은 글이고, **작성자가 아닐 때만** 참 |
| `alreadyReported` | `boolean` | 이미 신고했는지. 참이면 폼 대신 안내가 나온다. 물어볼 필요가 없는 상대에게는 묻지 않으므로 `canReport`가 거짓이면 언제나 거짓 |
| `reportForm` | `ReportForm` | 신고 사유. 검증 실패 시 입력을 담은 채 이 화면이 다시 그려진다 |

## 댓글 (DOMAIN.md 4.4, 6.4)

주소는 `GET /community/{postId}?comments=N`이다. `N`은 **몇 건까지 보여줄지**이고, 없으면 20이다.

- **최신 N건을 오래된 순으로** 보여준다. 잘라 내는 쪽이 과거여야 방금 쓴 댓글이 언제나 화면에 있다. 앞에서 자르면 댓글이 많은 글에서 자기가 쓴 댓글이 화면 밖에 남는데, 사용자에게는 등록이 안 된 것과 구분되지 않는다.
- `이전 댓글 더 보기`는 `N`을 20씩 늘린 주소로 가는 **링크**다. JS가 없어도 동작하고, 펼친 상태가 주소에 남아 새로고침·뒤로가기에서 유지된다.
- `N`에는 상한(200)이 있다. 주소로 들어오는 값이라 막지 않으면 `?comments=99999999` 하나로 한 게시글의 댓글을 전부 메모리에 올릴 수 있다. **상한에 막혀 못 보여주는 댓글이 남으면 링크를 감추지 않고 그 사실을 적는다** — 감추면 "댓글이 여기까지"로 보이는데 그것은 거짓이다.
- **작성 후에는** `?comments=` 없이 상세로 돌아간다. 새 댓글은 언제나 최신 20건 안에 있으므로 접혀도 보인다.
- **삭제 후에는 펼친 상태를 그대로 유지한다.** 작성과 다르다 — 삭제에는 성공 메시지가 없고 지운 자리의 `삭제된 댓글입니다.`가 **결과를 보여 주는 유일한 신호**인데, 20건으로 접어 버리면 최신 20건 밖의 댓글은 그 자리가 화면 밖으로 나간다. 사용자에게는 삭제가 안 된 것과 구분되지 않는다. 그래서 삭제 폼이 지금 값을 실어 보내고 리다이렉트가 되돌려 놓는다. (2026-08-04, PR #93 Codex 리뷰)
- **검증에 실패해 화면이 다시 그려질 때도 유지한다.** 어디로 간 것이 아니라 제자리다.

| 무엇 | 누구에게 |
|---|---|
| 댓글 목록 | 상세를 볼 수 있는 사람 전부 (비로그인 포함) |
| 댓글 작성 폼 | 로그인 회원 + `PUBLISHED` 글에만 |
| `댓글 삭제` 버튼 | 그 댓글의 작성자에게만 + `PUBLISHED` 글에만. 게시글 작성자에게도 관리자에게도 없다 (DOMAIN.md 6.7). **댓글 작성 폼과 같은 조건이다** — 삭제도 `PUBLISHED`를 요구하므로(6.4) 차단된 글에 버튼을 남기면 눌러도 403만 나오는 죽은 버튼이 된다 |

## 좋아요 (DOMAIN.md 6.5)

본문 바로 아래, 댓글 구역 위에 있다. 숫자는 누구에게나 보이고 버튼만 조건부다.

- **버튼 하나를 번갈아 누르는 토글이 아니다.** 추가는 `POST /community/{postId}/likes`, 취소는 `POST /community/{postId}/likes/delete`로 간다. 토글이면 재전송·더블클릭이 두 번 실행되어 원래 상태로 돌아가는데, 사용자에게는 눌렀는데 안 눌린 것으로 보인다.
- **화면에는 한 번에 버튼 하나만 있다.** `likedByViewer`가 둘을 가른다. 양쪽 다 멱등이라 둘을 함께 두어도 기능은 맞지만, 그러면 지금 내가 누른 상태인지 화면에서 알 수 없다.
- **링크가 아니라 폼이다.** 게시글·댓글 삭제와 같은 이유 — GET으로 상태가 바뀌면 링크 미리보기나 크롤러가 좋아요를 누른다.
- **좋아요 요청도 `?comments=`를 실어 보낸다.** 좋아요는 댓글 구역 위에 있어서, 댓글을 펼쳐 놓고 좋아요를 눌렀다가 20건으로 접혀 돌아오면 읽던 자리를 잃는다. 댓글 삭제와 같은 처리다.

| 무엇 | 누구에게 |
|---|---|
| 좋아요 개수 | 상세를 볼 수 있는 사람 전부 (비로그인 포함) |
| `좋아요` / `좋아요 취소` 버튼 | 로그인 회원 + `PUBLISHED` 글에만. **댓글 작성 폼과 같은 조건이다** (DOMAIN.md 4.5) |

## 신고 (DOMAIN.md 6.6)

좋아요 아래, 댓글 구역 위에 있다. 펼쳐야 보이는 폼이고 사유가 필수다.

- **남의 글에만 열린다.** 자기 글에는 이 자리가 아예 없다 — 자기 글이 문제라면 지우면 된다.
- **좋아요와 정반대로 멱등이 아니다.** 이미 신고한 글을 다시 신고하면 성공이 아니라 에러다. 조용히 성공을 돌려주면 접수됐다고 오해하지만 실제로는 아무 일도 일어나지 않는다. 그래서 **이미 신고한 사람에게는 폼 대신 안내**를 보여 준다 — 폼을 남겨 두면 눌렀을 때 오류 화면으로 튀는데, 그건 사용자가 잘못한 것이 아니라 이미 접수된 것이다.
- **취소가 없다.** 한 번 접수되면 되돌릴 수 없어서 폼을 접어 두고(`<details>`), 검증에 실패해 화면이 다시 그려질 때만 스스로 펼친다. 접힌 채로 돌아오면 오류 문구가 화면 밖에 남는다.
- **접수 결과는 flash 메시지로 알린다.** 신고는 화면에 아무 흔적도 남기지 않는다 — 내역은 관리자만 본다. 알리지 않으면 접수됐는지 알 방법이 없다. 게시글 삭제와 같은 이유다.
- **신고 요청도 `?comments=`를 실어 보낸다.** 좋아요와 같은 자리, 같은 이유다.

| 무엇 | 누구에게 |
|---|---|
| 신고 폼 | 로그인 회원 + `PUBLISHED` 글 + **작성자가 아닌 사람** + 아직 신고하지 않은 사람 |
| `이미 신고한 게시글입니다.` 안내 | 위 조건에서 이미 신고한 사람 |
| 신고 내역 (누가 무엇을 신고했는지) | **아무에게도 보이지 않는다.** 관리자 화면에만 나온다 |

## 무엇이 보이는가 (DOMAIN.md 4.3)

| 글 상태 | 비로그인·다른 회원 | 작성자 본인 |
|---|---|---|
| `PUBLISHED` | 정상 노출 | 정상 노출 |
| `BLOCKED` | **404** | 차단 안내 + 사유와 함께 노출 |
| `DELETED` | **404** | **404** (삭제는 종착 상태) |
| 없는 글 | 404 | 404 |

셋을 같은 404로 응답하는 것이 핵심이다. 403은 "그 자리에 글이 있다"는 사실을 흘린다. 관리자도 이 고객 화면에서는 일반 회원과 똑같이 취급한다.

## 화면 문자열

| 문자열 | 언제 보이나 | 고정한 테스트 |
|---|---|---|
| `← 목록` | 항상 | 없음 |
| `수정` | 작성자 본인 + 차단되지 않은 글일 때만 | `CommunityScreenRenderingTests.communityDetail_author_showsEditAndDeleteButtons` |
| `삭제` | 위와 같은 조건 | `CommunityScreenRenderingTests.communityDetail_author_showsEditAndDeleteButtons` |
| `조회` | 항상 | `CommunityScreenRenderingTests.communityDetail_rendersContent` |
| `좋아요` | 숫자는 항상. 버튼은 로그인 회원 + `PUBLISHED` 글 + 아직 안 누른 상태일 때 | `CommunityScreenRenderingTests.communityDetail_notLikedYet_showsLikeButton` |
| `좋아요 취소` | 로그인 회원 + `PUBLISHED` 글 + 이미 눌러 둔 상태일 때 | `CommunityScreenRenderingTests.communityDetail_alreadyLiked_showsCancelButton` |
| `댓글` | 항상 (구역 제목. 개수는 삭제된 댓글을 빼고 센다) | `CommunityScreenRenderingTests.communityDetail_rendersComments` |
| `삭제된 댓글입니다.` | 삭제된 댓글 자리. 작성자도 본문도 나오지 않는다 | `CommunityScreenRenderingTests.communityDetail_deletedComment_showsPlaceholderWithoutContent` |
| `아직 댓글이 없습니다.` | 댓글이 하나도 없을 때 | `CommunityScreenRenderingTests.communityDetail_withoutComments_showsEmptyMessage` |
| `댓글 등록` | 로그인 회원 + `PUBLISHED` 글일 때만 | `CommunityScreenRenderingTests.communityDetail_authenticated_showsCommentForm` |
| `등록한 댓글은 수정할 수 없습니다.` | 위와 같은 조건 (댓글에는 수정이 없다 — DOMAIN.md 6.4) | 없음 |
| `댓글 삭제` | 그 댓글의 작성자에게만 + `PUBLISHED` 글에만 | `CommunityScreenRenderingTests.communityDetail_ownComment_showsDeleteButton`, `CommunityScreenRenderingTests.communityDetail_blockedPostAuthor_hasNoDeadCommentDeleteButton` |
| `이 게시글 신고` | 로그인 회원 + `PUBLISHED` 글 + 작성자가 아니고 아직 신고하지 않았을 때 | `CommunityScreenRenderingTests.communityDetail_otherMember_rendersReportForm` |
| `접수된 신고는 취소할 수 없습니다.` | 위와 같은 조건 (신고에는 취소가 없다 — DOMAIN.md 6.6) | `CommunityScreenRenderingTests.communityDetail_otherMember_rendersReportForm` |
| `이미 신고한 게시글입니다.` | 이미 신고한 사람에게만. 폼 대신 나온다 | `CommunityScreenRenderingTests.communityDetail_alreadyReported_showsNoticeInsteadOfForm` |
| `로그인하면 댓글을 쓸 수 있습니다.` | 비로그인 + `PUBLISHED` 글일 때만. 차단된 글에는 띄우지 않는다 | `CommunityScreenRenderingTests.communityDetail_anonymous_showsLoginPromptInsteadOfForm` |
| `이전 댓글 더 보기` | 아직 못 보여준 댓글이 남았고 상한에 걸리지 않았을 때 | `CommunityScreenRenderingTests.communityDetail_manyComments_showsLoadMoreForOlderComments` |
| `남은 댓글` | 위와 같은 조건 | `CommunityScreenRenderingTests.communityDetail_manyComments_showsLoadMoreForOlderComments` |
| `오래된 댓글 일부는 표시하지 않습니다.` | 상한(200건)에 막혀 더 못 보여줄 때 | `CommunityScreenRenderingTests.communityDetail_beyondMaxComments_saysSoInsteadOfHidingSilently` |
| `white-space:pre-wrap` | 본문 영역. 사용자에게는 **줄바꿈이 살아난 본문**으로 보인다 | `CommunityScreenRenderingTests.communityDetail_rendersContent` |
| `(수정됨)` | `post.edited`, 즉 `updatedAt > createdAt`일 때 | `CommunityScreenRenderingTests.communityDetail_editedPost_showsEditedMark`, `CommunityMapperTests.increaseViewCount_doesNotMarkPostAsEdited` |
| `관리자가 차단한 게시글입니다.` | `BLOCKED` + 작성자 본인일 때만 | `CommunityScreenRenderingTests.communityDetail_blockedPost_author_showsBlockedReason` |
| `이 글은 다른 회원에게 보이지 않습니다.` | 위와 같은 조건 | `CommunityScreenRenderingTests.communityDetail_blockedPost_author_showsBlockedReason` |

## 눈으로는 안 잡히는 것

- **본문과 제목은 `th:text`로 낸다. `th:utext`를 쓰지 않는다** (DOMAIN.md 7). 바꿔도 정상 글에서는 화면이 똑같아 보이고, 누군가 `<script>`를 저장한 순간에만 드러난다. → `communityDetail_htmlInContent_isEscaped`, `communityDetail_titleWithHtml_isEscaped`
- **줄바꿈은 `<br>` 치환이 아니라 CSS로 살린다.** 치환하려면 `th:utext`가 필요해져서 위 규칙과 충돌한다.
- **차단 안내 블록은 평소 화면에 절대 안 나온다.** "차단된 글을 작성자가 연다"는 조건에서만 그려지므로, 표현식이 깨져도 사람 눈으로는 영원히 발견되지 않는다. 렌더링 테스트가 유일한 방어선이다.
- **차단된 글에는 작성자도 아무 조치를 할 수 없다.** 사유만 전달하고 수정·삭제 버튼을 주지 않는다 (DOMAIN.md 4.2 — `BLOCKED → DELETED` 금지). 버튼을 숨기는 것은 안내일 뿐이고 막는 것은 Service다 — 작성자는 이 화면에서 주소를 알게 되므로 요청만 따로 보낼 수 있다.
- **삭제는 링크가 아니라 폼이다.** GET으로 지워지면 링크 미리보기나 크롤러가 글을 없앨 수 있다. 댓글 삭제도 같다.
- **조회수는 노출되는 글에만 오른다.** 그리고 조회수가 올라도 `(수정됨)`이 켜지면 안 된다 — `posts.updated_at`이 `ON UPDATE CURRENT_TIMESTAMP`라 SQL에서 명시적으로 보존한다 (DOMAIN.md 6.2, 6.3).
- **댓글 검증 실패로 이 화면을 다시 그릴 때는 조회수가 오르지 않는다.** 폼이 되돌아오는 것은 조회가 아니다. 여기서 조회수를 올리면 빈 댓글을 여러 번 보내는 것만으로 숫자가 오르는데, 화면에는 그냥 숫자가 커질 뿐이라 원인을 찾을 수 없다.
- **댓글 개수와 "더 보기" 판단은 서로 다른 수를 쓴다.** 화면의 `댓글 N`은 삭제된 것을 빼고 세고, 더 펼칠 게 남았는지는 자리 표시까지 세야 한다 (DOMAIN.md 4.4). 하나로 합치면 개수가 부풀거나, 삭제된 댓글만 남은 구간에서 더 보기가 사라져 그 아래 댓글에 닿을 수 없다.
- **삭제된 댓글의 본문은 화면까지 내려오지 않는다.** SQL이 NULL로 지운다. 템플릿에서 감추는 것만으로는 응답 본문에 남아 있고, 자리 표시 마크업을 잘못 고치면 지워진 글이 되살아난다.
- **"더 보기"는 과거로 거슬러 올라간다.** 반대로 만들면 댓글이 많은 글에서 방금 쓴 댓글이 화면 밖에 남는다. 댓글이 20건 이하인 개발 화면에서는 어느 쪽이든 똑같아 보인다.
- **상한에 막힌 상태는 감추지 않고 적는다.** 링크만 사라지면 "댓글이 여기까지"로 보이는데, 그 화면은 200건이 넘어야 나오므로 사람 눈으로는 영원히 발견되지 않는다.
- **좋아요를 눌러도 `(수정됨)`이 켜지면 안 된다.** `like_count` 재계산이 `posts.updated_at`을 건드리기 때문인데, SQL에서 명시적으로 보존한다 (DOMAIN.md 6.5). 조회수와 같은 자리이고, 시드에 실제로 이 버그가 있어서 좋아요를 받은 글마다 `(수정됨)`이 붙어 있었다. → `CommunityMapperTests.recalculateLikeCount_doesNotMarkPostAsEdited`
- **비로그인에게는 버튼이 없고 숫자만 있다.** 그리고 "내가 눌렀는지"를 **묻지도 않는다** — 버튼이 없으므로 물어볼 것이 없고, 물으면 비로그인 상세마다 쿼리가 하나 는다. → `CommunityScreenRenderingTests.communityDetail_anonymous_showsLikeCountWithoutButton`
- **차단된 글에는 작성자에게도 좋아요 버튼이 없다.** 댓글 폼·댓글 삭제 버튼과 같은 조건이고 같은 이유다 — 남겨 두면 눌러도 403만 나오는 죽은 버튼이 된다. → `CommunityScreenRenderingTests.communityDetail_blockedPostAuthor_hasNoDeadLikeButton`
- **같은 버튼을 두 번 눌러도 숫자가 두 번 오르지 않는다.** 양쪽이 멱등이라 그렇고, 재계산이 실제 행 수를 다시 세므로 어긋날 자리가 없다. 화면에서는 정상 동작과 구분되지 않는다.

## 상세에 앞으로 붙는 것

없다. 댓글 목록·작성은 조각 3에서, 좋아요 버튼은 조각 4에서, 신고 폼은 조각 5에서 붙었다. 위 "댓글"·"좋아요"·"신고" 절을 본다.
