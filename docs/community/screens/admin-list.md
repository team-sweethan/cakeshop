# 관리자 목록 — `GET /admin/community`

> 이 파일의 형식과 검사 규칙은 `docs/community/SCREENS.md`에 있다.

- 상태: 구현됨 (조각 5)
- 템플릿: `admin/community/list.html`
- 핸들러: `CommunityAdminController.list`
- 접근: `hasRole("ADMIN")` (DOMAIN.md 5)

```
┌──────────┬──────────────────────────────────────────┐
│ [사이드바]│ 커뮤니티 관리                             │
│          │ [상태 전체][노출 중][차단됨][삭제됨]       │
│          │ [최신순][신고 많은 순]                     │
│          │ ┌──────────────────────────────────────┐ │
│          │ │번호│분류│제목│작성자│작성일│신고│상태│관리│
│          │ └──────────────────────────────────────┘ │
│          │ 차단된 게시글은 고객 화면에서 열람이 …     │
└──────────┴──────────────────────────────────────────┘
```

**고객 목록과 세 가지가 다르다.** 상태로 거르지 않는 것이 기본이고(관리자는 삭제·차단된 글까지 본다, DOMAIN.md 4.3), 미처리 신고 수가 한 칸을 차지하며, 정렬을 고를 수 있다.

**차단·해제 버튼은 이 화면에 없다.** 차단에는 사유가 필수인데(4.3) 목록에는 사유를 입력할 자리가 없다. 조치는 상세에서 한다. 목업에 있던 `제재`·`삭제` 버튼은 조각 5에서 걷어냈다 — 관리자에게 게시글 삭제 권한은 없다(6.7).

**작성자·제목 검색도 없다.** 목업에는 있었지만 규칙에 없던 기능이고, 관리자의 동선("신고된 글을 찾아 조치")은 정렬이 해결한다(6.7의 결정).

## 화면 문자열

| 문자열 | 언제 보이나 | 고정한 테스트 |
|---|---|---|
| `커뮤니티 관리` | 항상 | `CommunityScreenRenderingTests.communityAdminList_rendersForAdmin` |
| `상태 전체` | 항상 (필터) | 없음 |
| `노출 중` | 항상 (필터). 글 한 줄의 상태 배지로도 쓴다 | `CommunityScreenRenderingTests.communityAdminList_rendersForAdmin` |
| `차단됨` | 항상 (필터). 차단된 글의 상태 배지로도 쓴다 | `CommunityScreenRenderingTests.communityAdminDetail_blockedPost_showsBlockRecordAndUnblock` |
| `삭제됨` | 항상 (필터). 지워진 글의 상태 배지로도 쓴다 | `CommunityScreenRenderingTests.communityAdminDetail_deletedPost_hidesModerationActions` |
| `최신순` | 항상 (정렬) | 없음 |
| `신고 많은 순` | 항상 (정렬) | `CommunityScreenRenderingTests.communityAdminList_rendersForAdmin` |
| `미처리 신고` | 항상 (표 머리글) | 없음 |
| `상세보기` | 글 한 줄마다 | 없음 |
| `조건에 맞는 게시글이 없습니다.` | 필터 결과가 비었을 때만 | `CommunityScreenRenderingTests.communityAdminList_withoutPosts_rendersEmptyMessage` |
| `페이지 이동` | 글이 한 쪽(20건)을 넘을 때만 | 없음 |
| `차단된 게시글은 고객 화면에서 열람이 차단됩니다.` | 항상 (안내) | `CommunityScreenRenderingTests.communityAdminList_rendersForAdmin` |
