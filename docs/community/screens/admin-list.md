# 관리자 목록 — `GET /admin/community`

> 이 파일의 형식과 검사 규칙은 `docs/community/SCREENS.md`에 있다.

- 상태: 목업 (조각 5에서 연결)
- 템플릿: `admin/community/list.html`
- 핸들러: `CommunityAdminController.list`
- 접근: `hasRole("ADMIN")` (DOMAIN.md 5)

```
┌──────────┬──────────────────────────────────────────┐
│ [사이드바]│ 커뮤니티 관리                             │
│          │ [목업 안내]                               │
│          │ [작성자 검색][제목 검색][분류▾][상태▾][검색]│
│          │ ┌──────────────────────────────────────┐ │
│          │ │번호│분류│제목│작성자│작성일│상태│관리 │ │
│          │ │ 15 │후기│…  │단골  │07.21│정상│…    │ │
│          │ └──────────────────────────────────────┘ │
│          │ 제재된 게시글은 고객 화면에서 열람이 …     │
└──────────┴──────────────────────────────────────────┘
```

## 화면 문자열

| 문자열 | 언제 보이나 | 고정한 테스트 |
|---|---|---|
| `커뮤니티 관리` | 항상 | `CommunityScreenRenderingTests.communityAdminList_rendersForAdmin` |
| `작성자 검색` | 항상 (검색 입력) | 없음 |
| `제목 검색` | 항상 (검색 입력) | 없음 |
| `상세보기` | 글 한 줄마다 | 없음 |
| `제재된 게시글은 고객 화면에서 열람이 차단됩니다.` | 항상 (안내) | `CommunityScreenRenderingTests.communityAdminList_rendersForAdmin` |
| `mock-notice` | 항상. 아직 목업임을 알린다 | `CommunityScreenRenderingTests.communityAdminList_rendersForAdmin` |
