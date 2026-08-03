# 관리자 상세 — `GET /admin/community/{postId}`

> 이 파일의 형식과 검사 규칙은 `docs/community/SCREENS.md`에 있다.

- 상태: 목업 (조각 5에서 연결)
- 템플릿: `admin/community/detail.html`
- 핸들러: `CommunityAdminController.detail`
- 접근: `hasRole("ADMIN")`

왼쪽에 게시글 정보·본문·댓글, 오른쪽에 신고 내역과 모더레이션 패널이 있는 2단 구성이다. **차단된 글의 본문을 관리자가 볼 수 있는 유일한 화면**이다 — 고객 경로(`/community/{id}`)에서는 관리자도 404를 받는다 (DOMAIN.md 4.3).

## 화면 문자열

| 문자열 | 언제 보이나 | 고정한 테스트 |
|---|---|---|
| `게시글 정보` | 항상 | `CommunityScreenRenderingTests.communityAdminDetail_rendersForAdmin` |
| `모더레이션` | 항상 (오른쪽 패널) | `CommunityScreenRenderingTests.communityAdminDetail_rendersForAdmin` |
| `신고 내역` | 항상 | `CommunityScreenRenderingTests.communityAdminDetail_rendersForAdmin` |
| `제재 사유` | 항상 (차단 시 필수 입력) | 없음 |
| `목록으로` | 항상 | 없음 |
| `mock-notice` | 항상. 아직 목업임을 알린다 | `CommunityScreenRenderingTests.communityAdminDetail_rendersForAdmin` |

## 조각 5에서 정하거나 고쳐야 할 것

관리자 목업 두 화면은 **도메인 규칙보다 먼저 그려졌다.** 그래서 규칙에 없는 기능이 버튼으로 존재한다. 조각 5는 이 목록을 정리하는 일부터 시작한다.

| 지금 화면 | 무엇이 문제인가 |
|---|---|
| `게시글 삭제` / `게시글 영구 삭제` 버튼 | **DOMAIN.md에 관리자 삭제 권한이 없다.** 관리자 조치는 차단뿐이고(6.7), `BLOCKED → DELETED`는 금지다(4.2). 권한을 새로 정하든 버튼을 없애든 결정이 필요하다. "영구 삭제"라는 말은 soft delete와도 어긋난다 |
| 댓글마다 `삭제` 버튼 | 관리자의 댓글 삭제도 규칙에 없다. 댓글 삭제는 작성자 본인 기준이다 (6.4) |
| 상태 어휘가 `정상`/`제재` | 도메인 용어는 `PUBLISHED`/`BLOCKED`/`DELETED`이고 문서에서는 "차단"이라 부른다. 화면·문서·코드가 서로 다른 말을 쓰고 있다. 하나로 맞춘다 |
| `IP` 표시 | `posts`에 IP 컬럼이 없다. 넣으려면 스키마 변경이고, 개인정보라 보관 근거가 필요하다 |
| `첨부 이미지` 자리 | `post_images`는 1차에서 쓰지 않는다 (DOMAIN.md 2) |
| 작성자/제목 검색, 분류·상태 필터 | 관리자 목록의 필터·정렬은 **DOMAIN.md 9의 보류 항목**이다. 고객 목록에는 검색이 없다 |
| 분류 선택지 `후기/질문/자유/레시피` | 글쓰기 화면과 같은 문제. 활성 카테고리는 셋뿐이다 |
