# 관리자 차단·기각 — C1·C2·C3·C4

> 공통 규칙(상태 전이·접근 규칙·권한·입력 검증)은 `../DOMAIN.md`가 정본이다.
> 조각 순서와 진행 상태는 `../PLAN.md`. 기능 ID와 spec 라우팅 표는 `../DOMAIN.md` 1절.
> 조각: 5

관리자가 조치하는 경로는 차단(C3)·해제(C3)·신고 기각(C4) 셋이다. 그 밖의 것은 관리자 권한이 아니다 — 아래 C3의 마지막 항목이 정본이다.

## C1. 관리자 목록 (`GET /admin/community`)

상태 필터와 정렬 둘만 받는다 (2026-08-04 결정, `../DOMAIN.md` 9절 보류 해소).

- 상태: 전체 / `PUBLISHED` / `BLOCKED`. `DELETED`도 목록에는 나온다(관리자는 모든 상태를 본다, `../DOMAIN.md` 4.3).
- 정렬: 최신순(기본) / 미처리 신고 많은 순. `id` tiebreaker는 어느 정렬에도 붙인다(`community-read.md` B1과 같은 이유).
- 허용값은 `<choose>`로 매핑하고 `${}`로 잇지 않는다. 모르는 값은 기본값으로 떨어뜨린다.
- **작성자·제목 검색은 넣지 않는다.** 관리자가 하는 일은 "신고된 글을 찾아 조치하는 것"이고 그 동선은 정렬이 해결한다. 검색은 LIKE 4종과 인덱스 판단을 함께 불러오며, 필요해지면 그때 조각으로 세운다.
- 상태 어휘는 화면에서도 문서와 같은 말을 쓴다 — `노출 중`/`차단됨`/`삭제됨`. 목업의 `정상`/`제재`는 버린다.

## C2. 관리자 상세 (`GET /admin/community/{id}`)

- 모든 상태의 글이 정상 노출된다(`../DOMAIN.md` 4.3). 고객 경로는 관리자에게도 `DELETED`·`BLOCKED`를 404로 준다 — 조치는 관리자 경로에서만 한다.

## C3. 차단·해제 (`POST /admin/community/{id}/block` · `/unblock`)

- **관리자 수동 차단만.** 신고 누적 자동 차단·자동 숨김 없음.
- 근거: `blocked_by`가 `members.id` FK다. 자동 차단은 넣을 값이 없어 시스템 계정이나 암묵적 NULL 규칙이 필요하다. 스키마가 사람이 차단하는 것을 전제한다. 또한 임계값 방식은 담합 어뷰징 방어(신고자 신뢰도 등)를 불러오며 MVP를 벗어난다.
- 차단 시 `status='BLOCKED'`, `blocked_at`, `blocked_reason`, `blocked_by`를 함께 기록한다. 차단 사유는 필수다(`../DOMAIN.md` 7절) — 작성자에게 보여 주는 값이므로(4.3) 비어 있으면 차단 화면이 아무 말도 하지 않는다.
- 해제해도 `blocked_at`/`blocked_reason`/`blocked_by`를 NULL로 되돌리지 않는다(`../DOMAIN.md` 4.2).
- 차단·해제는 게시글 행을 잠그고 시작한다(`SELECT ... FOR UPDATE`). `community-reaction.md` A8과 같은 자리다 — 같은 행에 쓰면서 신고 상태까지 함께 바꾸므로, 잠금 없이 하면 조회수·좋아요 경로와 잠금 순서가 엇갈린다.
- 차단하면 그 글의 `PENDING` 신고가 전부 `RESOLVED`가 된다. 전이 규칙은 `community-reaction.md` A9가 정본이다.
- **관리자의 조치는 차단과 해제뿐이다.** 게시글 삭제도, 댓글 삭제도 관리자 권한이 아니다. 게시글 삭제는 작성자만(`../DOMAIN.md` 4.2, `BLOCKED → DELETED`는 금지), 댓글 삭제는 그 댓글의 작성자만 할 수 있다(`community-comment.md`). 관리자 화면에 그런 버튼을 두지 않는다.

## C4. 신고 기각 (`POST /admin/community/{id}/reports/reject`)

- 그 글의 `PENDING` 신고를 전부 `REJECTED`로 닫는다. 게시글 상태는 바뀌지 않는다.
- **작성자가 지운 글에는 기각도 할 수 없다.** 근거는 `community-reaction.md` A9.

## 검증

이 spec이 소유하는 하네스다. 인덱스는 `../PLAN.md`에 있다.

**H17 — 관리자 조치가 전이 규칙을 지킴.** 이미 차단된 글 재차단·지운 글 차단·차단된 적 없는 글 해제가 전부 0행이고, 0행이 성공으로 넘어가지 않으며, 차단이 `updated_at`을 보존한다. `CommunityMapperXmlTests` +3, `CommunityMapperTests` +6(실제 MariaDB), `CommunityAdminServiceTests`(11) — `InOrder`로 **잠그고 → 바꾸고 → 신고를 닫는** 순서까지 본다(`community-reaction.md` H15와 같은 자리). **재차단은 화면에 성공으로 보이고 사라지는 것은 첫 조치의 시각·사유라, 이 검사가 없으면 잃은 줄도 모른다.** 전이 규칙 자체는 `ReportStatusTests`(2)가 enum 표로 고정한다 — 처리된 신고에서 나가는 전이가 전부 거짓인 것이 핵심이다. `CommunityTransactionTests`(1)가 신고를 닫다 실패하면 차단도 함께 되돌아가는지 본다.

**H18 — 관리자 목록이 상태로 거르지 않는 것을 기본으로 두고, 정렬 분기마다 `id` tiebreaker를 유지하며, 미처리 신고만 셈.** 관리자 조치 경로가 Security 뒤에 있는 것까지. `CommunityMapperXmlTests` +4(분기마다 형태), `CommunityMapperTests` +4, `CommunityAdminControllerTests`(9), `CommunityScreenRenderingTests` +2(실제 필터 체인으로 403과 **DB가 안 바뀐 것**까지). **처리된 신고까지 세면 조치한 글이 목록 맨 위에 영원히 남아 진짜 처리할 글을 가리는데, 숫자만 다를 뿐 화면은 멀쩡하다.**
