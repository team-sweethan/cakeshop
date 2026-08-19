# 조각 5 — 신고·차단

> **끝난 조각의 기록이다. 지금 구속하지 않는다.**
> 이 조각이 만든 규칙의 정본은 `../specs/community-reaction.md`와 `../specs/community-admin.md`다.
> 조각 순서와 진행 상태는 `../PLAN.md`, 발견된 문제는 `../reviews/`,
> 방향을 고른 판단은 `../decisions/`에 있다.

- 중복 신고 에러 응답, 취소 불가
- 관리자 차단·해제, `blocked_*` 기록 및 해제 후 보존
- 보류 항목 결정: `post_reports.status` 전이, 관리자 목록 필터·정렬

**검증**: 중복 신고 거부, 비관리자의 차단 API 접근 거부(화면 숨김이 아니라 Security), 차단 해제 후 `blocked_*`가 남아 있는지.

**관리자 목업 두 화면은 도메인 규칙보다 먼저 그려졌다.** 규칙에 없는 기능이 버튼으로 존재한다 — 특히 `게시글 영구 삭제`와 댓글 `삭제`는 **DOMAIN.md에 없는 권한**이고, 관리자 조치는 차단뿐이며 `BLOCKED → DELETED`는 금지다(4.2, 6.7). 상태 어휘도 화면은 `정상`/`제재`, 문서는 `차단`으로 갈려 있다. 조각 5는 `screens/admin-detail.md`의 "조각 5에서 정하거나 고쳐야 할 것" 표를 정리하는 일부터 시작한다.

**완료 (2026-08-04)**. 보류 2건과 목업 정리를 먼저 확정하고(`../decisions/decision-log-1st.md`) 구현했다.

- **신고**: `CommunityMapper` +5 statement(`insertReport`·`existsReport`·`findReportsByPost`·~~`countPendingReports`~~(2026-08-18 삭제, `../decisions/decision-log-2nd.md`)·`closePendingReports`), `dto/form/ReportForm`, `dto/view/ReportView`, `CommunityService` 3개 메서드 + `requireReportablePost`, `CommunityController.report` + 상세 모델 확장(`canReport`·`alreadyReported`), `detail.html`에 접힌 신고 폼.
- **차단·해제·기각**: `ReportStatus`(전이 규칙 포함), `V20260804_074043__add_post_report_status_constraint.sql`, `CommunityMapper` +5 statement(`findPostsForAdmin`·`countPostsForAdmin`·`findPostByIdForAdmin`·`blockPost`·`unblockPost`), `dto/view` 3종(`AdminPostListView`·`AdminPostDetailView`·`AdminPostSort`), `dto/form/BlockForm`, 새 `CommunityAdminService`, `CommunityAdminController` 5개 핸들러, 관리자 템플릿 2종 전면 교체.
- migration은 CHECK 제약 하나뿐이다 — `post_reports`와 `posts.blocked_*`가 V0에 전부 있다. `SecurityConfig`도 그대로다. 새 경로는 `/admin/**` → `hasRole("ADMIN")`과 `anyRequest().authenticated()`에 걸린다.

검증은 `CommunitySchemaTests`, `CommunityMapperXmlTests`, `CommunityMapperTests`, `CommunityServiceTests`, `CommunityAdminServiceTests`, `CommunityControllerTests`, `CommunityAdminControllerTests`, `CommunityScreenRenderingTests`로 고정했다. 하네스 표에 H16·H17·H18을 올렸다.

**관리자 서비스를 따로 뒀다.** 고객 경로는 "노출 중인 글만"이 기본이고 그 판단이 거의 모든 메서드에 붙어 있는데, 관리자 경로는 **모든 상태를 보는 것이 기본**이다(4.3). 한 클래스에 섞으면 노출 판단을 빠뜨린 메서드가 고객 경로에서 호출되는 날이 오고, 그때 새는 것은 차단된 글의 본문이다.

**`posts` 행에 쓰는 세 번째 경로였고, 이번에는 처음부터 잠그고 시작했다.** 조회수(H13)·좋아요(H15)와 같은 자리다. 차단은 `posts`와 `post_reports`를 함께 바꾸므로 잠금 없이 하면 좋아요·조회수 경로와 순서가 엇갈린다. `lockPost`를 그대로 재사용했다 — 조인이 없는 것이 이 자리에서도 그대로 필요했다.

**전이 판단을 `PostStatus.canTransitionTo`에 맡겼다.** 관리자 경로에 조건을 새로 적으면 전이 규칙이 두 벌이 되고, enum만 고치는 날 이 경로만 옛 규칙으로 남는다. `BLOCKED → BLOCKED`가 거짓인 덕분에 "이미 차단된 글 다시 차단"이 자동으로 막혔다 — 막지 않으면 원래 조치의 시각과 사유가 덮이는데 화면에는 성공으로 보인다.

**신고는 좋아요와 정반대라 코드에서 그 대비를 두 번 적었다.** 좋아요는 중복이 멱등 성공, 신고는 중복이 에러다(6.6). 그래서 `insertReport`에는 `IGNORE`도 `ON DUPLICATE KEY`도 없고, XML 형태 검사가 그 부재를 고정한다 — 나중에 "여기도 좋아요처럼" 하고 붙이면 중복 신고가 조용히 성공한다.

구현 중 걸린 것 하나: **Thymeleaf가 HTML 주석을 응답에 그대로 내보낸다.** 관리자 상세 주석에 "이 버튼은 없앴다"고 적으면서 없앤 문구를 그대로 썼더니, 그 문구가 화면에 없다고 단언하는 테스트가 주석 때문에 실패했다. 고객 상세에서 이미 겪고 적어 둔 함정인데 같은 자리에서 다시 밟았다.
