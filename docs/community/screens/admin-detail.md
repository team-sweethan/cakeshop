# 관리자 상세 — `GET /admin/community/{postId}`

> 이 파일의 형식과 검사 규칙은 `docs/community/SCREENS.md`에 있다.

- 상태: 구현됨 (조각 5)
- 템플릿: `admin/community/detail.html`
- 핸들러: `CommunityAdminController.detail`
- 접근: `hasRole("ADMIN")`

왼쪽에 게시글 정보·본문·댓글, 오른쪽에 신고 내역과 모더레이션 패널이 있는 2단 구성이다. **차단된 글의 본문을 관리자가 볼 수 있는 유일한 화면이다** — 고객 경로(`/community/{id}`)에서는 관리자도 404를 받는다 (DOMAIN.md 4.3).

## 관리자가 할 수 있는 일

| 조치 | 주소 | 언제 보이나 |
|---|---|---|
| 차단 (사유 필수) | `POST /admin/community/{postId}/block` | `PUBLISHED`일 때만 |
| 차단 해제 | `POST /admin/community/{postId}/unblock` | `BLOCKED`일 때만 |
| 신고 기각 | `POST /admin/community/{postId}/reports/reject` | 미처리 신고가 있고 `DELETED`가 아닐 때만 |

**이 셋뿐이다**(DOMAIN.md 6.7). 게시글 삭제도 댓글 삭제도 관리자 권한이 아니다 — 게시글 삭제는 작성자만(4.2의 `BLOCKED → DELETED` 금지), 댓글 삭제는 그 댓글의 작성자만 할 수 있다(6.4).

**작성자가 지운 글(`DELETED`)에는 아무 버튼도 보이지 않는다.** 차단·해제는 종착 상태라 불가능하고(4.2), **기각도 막는다** — `REJECTED`는 "관리자가 보고 문제없다고 판단했다"는 기록인데 판단할 글이 없어졌기 때문이다(6.6). 그래서 기각 버튼 조건에는 미처리 신고 수만이 아니라 `post.deleted`가 함께 붙는다. 하나만 걸면 "조치할 수 없습니다" 안내와 기각 버튼이 나란히 보이는 화면이 된다.

## 댓글

댓글은 고객 상세와 같은 `CommentSectionView`를 쓰고 `?comments=`도 똑같이 받는다. **여기에 "더 보기"가 있어야 하는 이유는 관리자에게 대체 경로가 없다는 것이다** — 차단되거나 삭제된 글은 고객 화면(`/community/{id}`)에서 열리지 않으므로, 이 화면에서 잘린 댓글은 어디서도 볼 수 없다. 상한(200건)에 막히면 링크를 조용히 감추지 않고 그 사실을 적는다.

차단 사유 검증이 실패해 상세를 다시 그릴 때는 펼친 수를 잃고 기본값으로 접힌다. 그 자리에서 관리자가 보려던 것은 댓글이 아니라 비어 있는 차단 사유다.

차단을 해제해도 `blocked_at`·`blocked_reason`·`blocked_by`와 신고 상태 `RESOLVED`는 남는다(4.2, 6.6). 그래서 "차단됐다가 풀린 글"은 상태가 `노출 중`인데 차단 기록이 함께 보인다.

## 조각 5에서 정리한 것

관리자 목업 두 화면은 도메인 규칙보다 먼저 그려져서, 규칙에 없는 기능이 버튼으로 존재했다. 조각 5에서 아래와 같이 정리했다.

| 목업에 있던 것 | 어떻게 했나 |
|---|---|
| `게시글 삭제` / `게시글 영구 삭제` 버튼 | **삭제.** 관리자 조치는 차단뿐이고(6.7) `BLOCKED → DELETED`는 금지다(4.2) |
| 댓글마다 `삭제` 버튼 | **삭제.** 댓글 삭제는 그 댓글의 작성자만 할 수 있다(6.4) |
| 상태 어휘 `정상`/`제재` | **`노출 중`/`차단됨`/`삭제됨`으로 통일.** 화면·문서·코드가 같은 말을 쓴다(6.7) |
| `IP` 표시 | **삭제.** `posts`에 IP 컬럼이 없고, 넣으면 개인정보를 새로 저장하게 된다 |
| `첨부 이미지` 자리 | **삭제.** `post_images`는 1차에서 쓰지 않는다 (DOMAIN.md 2) |
| 작성자/제목 검색, 분류 필터 | **삭제.** 관리자 목록은 상태 필터와 정렬만 받는다 (6.7) |
| 분류 선택지 `후기/질문/자유/레시피` | **삭제.** 관리자는 글을 고치지 않으므로 분류를 고를 자리가 없다 |

## 화면 문자열

| 문자열 | 언제 보이나 | 고정한 테스트 |
|---|---|---|
| `게시글 정보` | 항상 | `CommunityScreenRenderingTests.communityAdminDetail_rendersForAdmin` |
| `모더레이션` | 항상 (오른쪽 패널) | `CommunityScreenRenderingTests.communityAdminDetail_rendersForAdmin` |
| `신고 내역` | 항상 | `CommunityScreenRenderingTests.communityAdminDetail_rendersForAdmin` |
| `접수된 신고가 없습니다.` | 신고가 하나도 없을 때만 | 없음 |
| `미처리` | 아직 처리하지 않은 신고 줄에만 | `CommunityScreenRenderingTests.communityAdminDetail_rendersForAdmin` |
| `처리 완료` | 차단으로 닫힌 신고 줄에만 | 없음 |
| `기각됨` | 기각으로 닫힌 신고 줄에만 | 없음 |
| `차단 사유` | 항상 (차단 폼의 입력, 차단 기록의 항목) | `CommunityScreenRenderingTests.communityAdminDetail_rendersForAdmin` |
| `차단하기` | `PUBLISHED`일 때만 | 없음 |
| `차단 해제` | `BLOCKED`일 때만 | `CommunityScreenRenderingTests.communityAdminDetail_blockedPost_showsBlockRecordAndUnblock` |
| `신고 기각` | 미처리 신고가 있고 `DELETED`가 아닐 때만 | `CommunityScreenRenderingTests.communityAdminDetail_rendersForAdmin` |
| `작성자가 삭제한 게시글이라 조치할 수 없습니다.` | `DELETED`일 때만 | `CommunityScreenRenderingTests.communityAdminDetail_deletedPost_hidesModerationActions` |
| `삭제된 댓글입니다.` | 지워진 댓글 자리에만 | 없음 |
| `아직 댓글이 없습니다.` | 댓글이 하나도 없을 때만 | 없음 |
| `이전 댓글 더 보기` | 아직 안 보여준 댓글이 있고 더 늘릴 여지가 있을 때만 | 없음 |
| `오래된 댓글 일부는 표시하지 않습니다.` | 상한(200건)에 막혀 더 못 늘릴 때만 | 없음 |
| `목록으로` | 항상 | 없음 |
