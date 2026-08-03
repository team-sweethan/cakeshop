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
│  좋아요 3                                            │
│  ───────────────────────────────────────────────    │
│  댓글                                                │
│  ┌───────────────────────────────────────────────┐  │
│  │ 댓글 기능은 준비 중입니다.        ← 조각 3      │  │
│  └───────────────────────────────────────────────┘  │
├─────────────────────────────────────────────────────┤
│ [공통 푸터 fragment]                                 │
└─────────────────────────────────────────────────────┘
```

## 모델

| 이름 | 타입 | 내용 |
|---|---|---|
| `post` | `PostDetailView` | 글 본문과 상태. `blocked`, `edited`, `authorName()`은 뷰가 계산한다 |

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
| `조회` | 항상 | `CommunityScreenRenderingTests.communityDetail_rendersContent` |
| `좋아요` | 항상 (숫자만. 누르는 버튼은 조각 4) | 없음 |
| `댓글` | 항상 (구역 제목) | 없음 |
| `white-space:pre-wrap` | 본문 영역. 사용자에게는 **줄바꿈이 살아난 본문**으로 보인다 | `CommunityScreenRenderingTests.communityDetail_rendersContent` |
| `(수정됨)` | `post.edited`, 즉 `updatedAt > createdAt`일 때 | `CommunityScreenRenderingTests.communityDetail_editedPost_showsEditedMark`, `CommunityMapperTests.increaseViewCount_doesNotMarkPostAsEdited` |
| `관리자가 차단한 게시글입니다.` | `BLOCKED` + 작성자 본인일 때만 | `CommunityScreenRenderingTests.communityDetail_blockedPost_author_showsBlockedReason` |
| `이 글은 다른 회원에게 보이지 않습니다.` | 위와 같은 조건 | `CommunityScreenRenderingTests.communityDetail_blockedPost_author_showsBlockedReason` |
| `댓글 기능은 준비 중입니다.` | 항상 (조각 3에서 실제 댓글로 교체) | 없음 |

## 눈으로는 안 잡히는 것

- **본문과 제목은 `th:text`로 낸다. `th:utext`를 쓰지 않는다** (DOMAIN.md 7). 바꿔도 정상 글에서는 화면이 똑같아 보이고, 누군가 `<script>`를 저장한 순간에만 드러난다. → `communityDetail_htmlInContent_isEscaped`, `communityDetail_titleWithHtml_isEscaped`
- **줄바꿈은 `<br>` 치환이 아니라 CSS로 살린다.** 치환하려면 `th:utext`가 필요해져서 위 규칙과 충돌한다.
- **차단 안내 블록은 평소 화면에 절대 안 나온다.** "차단된 글을 작성자가 연다"는 조건에서만 그려지므로, 표현식이 깨져도 사람 눈으로는 영원히 발견되지 않는다. 렌더링 테스트가 유일한 방어선이다.
- **차단된 글에는 작성자도 아무 조치를 할 수 없다.** 사유만 전달하고 수정·삭제 버튼을 주지 않는다 (DOMAIN.md 4.2 — `BLOCKED → DELETED` 금지).
- **조회수는 노출되는 글에만 오른다.** 그리고 조회수가 올라도 `(수정됨)`이 켜지면 안 된다 — `posts.updated_at`이 `ON UPDATE CURRENT_TIMESTAMP`라 SQL에서 명시적으로 보존한다 (DOMAIN.md 6.2, 6.3).

## 상세에 앞으로 붙는 것

새 페이지가 생기는 게 아니라 **이 화면이 채워진다.** 조각을 진행하며 이 표의 줄을 위 문자열 표로 옮긴다.

| 무엇 | 조각 | 화면에 어떻게 나타나나 | 미리 정해진 것 |
|---|---|---|---|
| 수정·삭제 버튼 | 2 | 상단 `← 목록` 줄 오른쪽. **작성자 본인에게만.** 차단된 글에는 작성자에게도 주지 않는다 | 삭제는 `DELETED` 전이. 확인 창을 거친다 |
| 댓글 목록·작성 | 3 | `댓글 기능은 준비 중입니다.` 자리를 대체 | 1단계만. 삭제된 댓글은 자리 표시로 남고 개수에서 빠진다 (DOMAIN.md 4.4) |
| 좋아요 버튼 | 4 | 지금 숫자만 있는 `좋아요 N` 자리 | POST/DELETE 분리, 둘 다 멱등. **"내가 눌렀는지" 표시 여부는 DOMAIN.md 9의 보류 항목** |
| 신고 버튼 | 5 | 본문 아래 | 중복 신고는 에러. 취소 불가 |
