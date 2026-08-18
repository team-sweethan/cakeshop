# 조각 14 — 공지사항

> **끝난 조각의 기록이다. 지금 구속하지 않는다.**
> 이 조각이 만든 규칙의 정본은 `../specs/community-notice.md`다.
> 조각 순서와 진행 상태, 위험과 결정 로그는 `../PLAN.md`.

규칙의 정본은 `../specs/community-notice.md`다. 여기에는 **무엇을 어떤 순서로 만드는지**만 둔다.

셋으로 나눈 기준은 "그 조각만으로 브라우저에서 확인이 되는가"다. 표만 만들고 끝나는 조각은 두지
않는다 — 확인할 화면이 없으면 다음 조각에서 되돌아온다.

## 14a — 표와 관리자 CRUD (E4·C5·C6·C7) — **완료**

**왜 먼저였나**: 표가 없으면 아무것도 못 하고, 관리자 CRUD가 없으면 고객 화면에 보여 줄 공지를
만들 방법이 시드밖에 없다. 관리자 화면까지 닿아야 14b를 손으로 확인할 수 있다.

- migration `V20260812_065639__add_community_notices.sql` — 표 + 상태 `CHECK` + **기간 `CHECK`**
- `Notice` entity, `NoticeStatus` enum(`PUBLISHED → DELETED`만 허용, `MemberStatus`·`PostStatus` 선례)
- `CommunityNoticeMapper` + XML **하나**. 게시글처럼 고객·관리자로 나누지 않는다 — 14b의
  `<sql id="visibleNotice">`를 고객 조회 셋이 `<include>`로만 써야 하는데, 네임스페이스가 갈리면
  건너 참조하거나 복사하게 되고 spec E4가 막으려던 자리가 그대로 열린다. 대신 노출 조건을 **걸지
  않는** 관리자 조회는 `selectAdmin*` 이름으로 뗐다(모든 상태·모든 기간)
- dto: `NoticeForm`, `NoticeUpdateCommand`, `AdminNoticeListRow`·`AdminNoticeDetailRow`·`NoticeLockRow`,
  `AdminNoticeListView`·`AdminNoticeDetailView`, `NoticeDisplayStatus`(`예정`/`노출 중`/`종료`/`삭제됨`)
- `CommunityNoticeAdminService` — `Clock` 주입, 잠금 → 전이 확인 → 조건부 UPDATE → 0행 거절
  (`CommunityAdminService`와 같은 순서)
- `CommunityNoticeAdminController` (`/admin/community/notices/**`)
- 화면: `templates/admin/community/notice/{list,form}.html`. 진입점은 **관리자 커뮤니티 목록의
  버튼**이다 — 사이드바(`fragments/admin/**`)는 공통 협의 파일이라 건드리지 않았다
- `seed-community.sql` 9절에 네 상태 샘플. **시각은 실행일 기준 상대값**(2026-08-11 결정 로그)

**검증**: H39·H40·H41(근거는 spec `검증` 절). 그 밖에 상태 전이 enum 표, 재삭제 0행, `DELETED`
공지 수정 거절, 폼 기간 검증, 관리자 목록 렌더링을 각 계층 테스트가 본다.

## 14b — 고객 노출 (B8 상단 영역 · B9 전체보기 · B10 상세) — **완료**

- `<sql id="visibleNotice">` 하나와 그것을 `<include>`하는 조회 3종: 상단·전체보기가 함께 쓰는
  목록, 개수, 상세 1건. 정렬도 `<sql id="visibleNoticeOrder">` 하나다
- `CommunityNoticeService` — 자리별 건수 상수(목록 상단 10), 페이지 크기 20, `now`를 `Clock`으로
  만들어 Mapper에 넘김. **상단 영역의 "1쪽 + 필터 없음"은 Controller가 아니라 여기 있다**
  (인기글 D7과 같은 자리)
- `CommunityNoticeController` (`GET /community/notices`, `/community/notices/{id}`)
- `CommunityController` 목록 모델에 상단 영역 추가
- **`SecurityConfig`에 고객 경로 둘을 따로 적었다** — 기존 `/community/{id:\d+}`가 숫자만 받아
  `"notices"`가 걸리지 않는다. 안 적으면 비로그인이 로그인 화면으로 튕긴다
- 화면: `customer/community/notice/{list,detail}.html` 신설, `list.html` 최상단에 영역(**인기글보다 위**)과 전체보기 링크
- `detail.html`을 재사용하지 않는다. **폴더를 나누는 것이 그 결정을 드러내는 자리다** — 같은
  폴더에 `detail.html`과 나란히 두면 다음 사람이 재사용을 먼저 떠올린다

**검증**: H42·H43·H44(근거는 spec `검증` 절). 그 밖에 정렬 키와 화면 날짜가 같은 값인지, 404 규칙,
페이징, 본문 이스케이프를 각 계층 테스트가 본다.

## 14c — 메인 노출 (D3) — **완료**

- `CommunityHomeQueryService.getNoticeSection()`(3건). **새 계약 클래스를 만들지 않았다**
- `HomeService`·`HomeController`·`main.html`
- `home`은 공통 협의 도메인이라 PR에서 확인을 받는다(조각 13과 같다)

**자리는 서비스 안내와 카테고리 사이로 정했다**(2026-08-12, 사용자 결정 변경).

**검증**: H45(근거는 spec `검증` 절) — 건수 3, 빈 영역, 그리고 **서비스 안내 < 공지 < 카테고리 순서인지**.
