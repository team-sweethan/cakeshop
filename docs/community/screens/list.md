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
│  인기글           2026.03.01 기준  ← 1쪽 + 필터 없을 때 │
│  ┌───────────────────────────────────────────────┐  │
│  │ 1  [질문] 딸기 케이크 보관법                   │  │
│  │ 2  [후기] 생일 케이크 주문 후기                │  │
│  └───────────────────────────────────────────────┘  │
│  … 최대 10건. 숫자(조회·좋아요·댓글)는 싣지 않는다 …   │
│                                                     │
│  (전체) (질문) (후기) (자유)   ← 활성 카테고리만      │
│  (최신순) (조회수순)           ← 활성 정렬만 강조      │
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
| `selectedSort` | `PostSort` | 선택된 정렬. 모르는 값이 들어와도 `LATEST`다 — `null`이 되지 않는다 |
| `popularSection` | `PopularSectionView` | 인기글 영역. **언제나 있고 `null`이 아니다** — 안 실을 때는 빈 영역이 온다 (DOMAIN.md 6.9) |

## 화면 문자열

| 문자열 | 언제 보이나 | 고정한 테스트 |
|---|---|---|
| `커뮤니티` | 항상 (제목) | `CommunityScreenRenderingTests.communityList_rendersPostRow` |
| `글쓰기` | 항상 (`/community/new`로 가는 버튼) | 없음 |
| `인기글` | 1쪽 + 카테고리 필터 없음 + 그릴 순위가 있을 때만 (DOMAIN.md 6.9) | `CommunityScreenRenderingTests.communityList_withConfirmedRanking_rendersPopularSection` |
| `기준` | 인기글 영역이 보일 때. 앞에 확정 날짜가 붙는다 (`2026.03.01 기준`) | `CommunityScreenRenderingTests.communityList_withConfirmedRanking_rendersPopularSection` |
| `전체` | 항상 (필터 해제) | 없음 |
| `최신순` | 항상 (기본 정렬) | `CommunityScreenRenderingTests.communityList_sortLinks_keepCategoryFilter` |
| `조회수순` | 항상 | `CommunityScreenRenderingTests.communityList_sortLinks_keepCategoryFilter` |
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
- **필터·정렬·쪽 번호는 주소로 표현한다.** 새로고침·뒤로가기·링크 공유에서 유지되어야 하므로 자바스크립트 상태로 두지 않는다.
- **필터 링크와 정렬 링크는 서로의 현재 값을 함께 싣는다.** 안 실으면 분류를 고른 뒤 조회수순을 누르는 순간 분류가 풀리는데, 목록은 멀쩡히 그려지고 글만 늘어나서 사용자에게는 정렬이 이상하게 동작한 것으로 보인다. 쪽 이동 링크도 셋을 다 싣는다. → `communityList_sortLinks_keepCategoryFilter`, `communityList_categoryLinks_keepSortOption`
- **모르는 `?sort=` 값은 오류가 아니라 최신순이다.** 목록은 공개 화면이라 주소가 망가졌다고 오류 페이지를 주지 않는다. 허용값은 `PostSort`로 좁히고 SQL은 `<choose>`로 갈리므로, 이상한 값은 **쿼리에 닿지도 않는다**(`${}`로 이으면 그대로 쿼리가 된다). → `CommunityControllerTests.list_invalidSortOption_fallsBackToLatestInsteadOfFailing`
- **조회수순에도 `id` tiebreaker가 붙는다.** 조회수는 0이 대부분이라 동점이 작성 시각보다 훨씬 잦고, 없으면 페이지 경계에서 글이 중복되거나 사라진다. → H28
- **페이지 번호는 `startPage`~`endPage`만 그린다.** 전체 쪽 수만큼 번호를 뿌리면 글이 늘수록 화면이 무너진다.
- **`이전`/`다음`은 한 쪽씩이 아니라 번호 블록 단위로 이동한다.** `PageNavigation`의 정의이며, 한 쪽씩 이동하도록 바꾸면 다른 화면과 동작이 달라진다.
- **본문 미리보기가 없다.** `content`는 TEXT라 목록에서 SELECT하지 않는다 (DOMAIN.md 6.1). 미리보기를 넣으려면 쿼리부터 바뀐다.
- **인기글을 실을지 말지는 템플릿이 아니라 Service가 정한다.** 템플릿은 `popularSection.isEmpty()`만 본다. 조건(1쪽 + 필터 없음)을 화면에 두면 화면이 늘 때마다 같은 규칙이 한 벌씩 늘고, 두 벌이 되는 순간 갈린다. → `CommunityServiceTests.getPopularSection_categoryFiltered_doesNotQueryAtAll`, `getPopularSection_secondPage_doesNotQueryAtAll`
- **인기글 줄에는 숫자가 없다.** 같은 글이 아래 목록에도 나오고 그쪽은 **현재** 조회·좋아요·댓글을 보여 준다. 한 화면에 같은 글의 숫자가 둘이면 어느 쪽도 못 믿을 값이 된다 (DOMAIN.md 6.9).
- **확정 날짜를 함께 낸다.** 인기글은 실시간이 아니라 스냅샷이고 배치를 거르면 어제 것으로 폴백한다. 날짜가 없으면 낡은 순위를 오늘 것으로 읽게 되는데, **그 화면은 정상일 때와 똑같이 생겼다.**
- **그릴 것이 없으면 영역이 통째로 사라진다.** 제목만 남기고 안을 비우면 상단에 빈 칸이 남아 사용자에게는 고장으로 보이는데, 서버에는 오류가 없어 로그에도 안 남는다. 첫 배포 직후(확정된 실행 없음)와 오른 글이 전부 지워지거나 차단된 경우 둘 다 여기 해당한다. → `communityList_everyRankedPostHidden_omitsPopularSection`
- **이 영역은 개발 환경에서 대개 화면에 없다.** 배치를 한 번도 안 돌리면 확정된 순위가 없어서 표현식이 깨져도 목록은 멀쩡히 뜬다. 렌더링 테스트가 유일한 방어선이다.
