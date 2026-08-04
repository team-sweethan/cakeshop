# 목록 — `GET /community`

> 이 파일의 형식과 검사 규칙은 `docs/community/SCREENS.md`에 있다.

- 상태: 구현됨 (조각 1)
- 템플릿: `customer/community/list.html`
- 핸들러: `CommunityController.list`
- 접근: 공개. 비로그인도 전부 볼 수 있다 (DOMAIN.md 5)

```
┌─────────────────────────────────────────────────────┐
│ [공통 헤더 fragment]                                 │
├─────────────────────────────────────────────────────┤
│  커뮤니티                                  [ 글쓰기 ] │
│                                                     │
│  (전체) (질문) (후기) (자유)   ← 활성 카테고리만      │
│                                                     │
│  ┌───────────────────────────────────────────────┐  │
│  │ [질문] 딸기 케이크 보관법            좋아요 3  │  │
│  │ 케이크덕후 · 2026.03.01 · 댓글 2 · 조회 41    │  │
│  └───────────────────────────────────────────────┘  │
│  … 한 쪽에 최대 20건, 최신순 …                       │
│                                                     │
│           (이전) (1) (2) (3) (다음)  ← 2쪽 이상일 때 │
├─────────────────────────────────────────────────────┤
│ [공통 푸터 fragment]                                 │
└─────────────────────────────────────────────────────┘
```

## 모델

| 이름 | 타입 | 내용 |
|---|---|---|
| `pageResult` | `PageResult<PostListView>` | 현재 쪽의 글 목록과 쪽 정보 |
| `pageNavigation` | `PageNavigation` | 표시할 번호 구간(`startPage`~`endPage`)과 앞뒤 블록 존재 여부 |
| `categories` | `List<PostCategoryView>` | **활성** 카테고리만 (DOMAIN.md 6.8) |
| `selectedCategoryId` | `Long` | 선택된 필터. 없으면 `null` |

## 화면 문자열

| 문자열 | 언제 보이나 | 고정한 테스트 |
|---|---|---|
| `커뮤니티` | 항상 (제목) | `CommunityScreenRenderingTests.communityList_rendersPostRow` |
| `글쓰기` | 항상 (`/community/new`로 가는 버튼) | 없음 |
| `전체` | 항상 (필터 해제) | 없음 |
| `좋아요` | 글 한 줄마다 | `CommunityScreenRenderingTests.communityList_rendersPostRow` |
| `댓글` | 글 한 줄마다. 삭제된 댓글은 세지 않는다 (DOMAIN.md 4.4) | `CommunityScreenRenderingTests.communityList_rendersPostRow` |
| `조회` | 글 한 줄마다 | `CommunityScreenRenderingTests.communityList_rendersPostRow` |
| `아직 등록된 글이 없습니다.` | 이 쪽에 글이 하나도 없을 때 | `CommunityScreenRenderingTests.communityList_withoutPosts_rendersEmptyMessage` |
| `페이지 이동` | 2쪽 이상일 때 (`nav`의 `aria-label`) | `CommunityScreenRenderingTests.communityList_multiplePages_rendersPageNavigation` |
| `이전` | 2쪽 이상일 때. 앞 블록이 없으면 `btn--disabled`로 남는다 | `CommunityScreenRenderingTests.communityList_multiplePages_rendersPageNavigation` |
| `다음` | 2쪽 이상일 때. 뒤 블록이 없으면 `btn--disabled`로 남는다 | `CommunityScreenRenderingTests.communityList_multiplePages_rendersPageNavigation` |

## 화면에 있지만 문자열 표에 없는 것

`게시글을 삭제했습니다.` — 글을 지우고 돌아오면 상단에 뜬다. 이 문구는 **템플릿이 아니라 `CommunityController`가 가진다**(`successMessage` flash → `fragments/common/alert`). 문자열 표는 템플릿 원문과 대조하는 검사이므로(`SCREENS.md` 2번) 여기 적으면 화면이 멀쩡한데도 빌드가 깨진다. 대신 `CommunityScreenRenderingTests.communityList_afterDelete_showsSuccessMessage`가 실제 렌더링 결과로 지킨다.

삭제 결과는 목록 어디에도 남지 않는다. 이 안내가 사라지면 사용자는 글이 지워졌는지 알 수 없고, 화면은 평소와 똑같아 보인다.

## 눈으로는 안 잡히는 것

- **작성자 이름은 `post.authorName()`으로 낸다.** 탈퇴 회원이면 닉네임 대신 `탈퇴한 회원`이 나온다 (DOMAIN.md 8). 템플릿에서 `post.authorNickname`을 직접 쓰면 탈퇴 회원 닉네임이 그대로 노출되는데, 화면은 멀쩡해 보인다. → `communityList_withdrawnAuthor_showsPlaceholderName`
- **필터와 쪽 번호는 주소로 표현한다.** 새로고침·뒤로가기·링크 공유에서 유지되어야 하므로 자바스크립트 상태로 두지 않는다.
- **페이지 번호는 `startPage`~`endPage`만 그린다.** 전체 쪽 수만큼 번호를 뿌리면 글이 늘수록 화면이 무너진다.
- **`이전`/`다음`은 한 쪽씩이 아니라 번호 블록 단위로 이동한다.** `PageNavigation`의 정의이며, 한 쪽씩 이동하도록 바꾸면 다른 화면과 동작이 달라진다.
- **본문 미리보기가 없다.** `content`는 TEXT라 목록에서 SELECT하지 않는다 (DOMAIN.md 6.1). 미리보기를 넣으려면 쿼리부터 바뀐다.
