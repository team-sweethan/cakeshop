# 조각 3 — 조회·노출 (#110, PR #179 머지 완료)

> **끝난 조각의 기록이다. 지금 구속하지 않는다.**
> 이 조각이 만든 규칙의 정본은 `../specs/review-read.md`.
> 조각 순서와 진행 상태는 `../PLAN.md`, 발견된 문제는 `../reviews/`,
> 방향을 고른 판단은 `../decisions/`에 있다.

**새로 만든 계약 3건.** 시그니처는 전부 `../DOMAIN.md` 2.7 표에 있다. 셋 다 담당자를 PR 리뷰어로 지정해 확인받는다. **앞의 둘은 남의 파일을 고치지 않았고, 세 번째만 고쳤다** — 그 자리는 08-06 합의가 이미 정해 둔 곳이다.

- **수민님** — `MemberReviewQueryService.getMembersByIds` (신규 파일 4개). `members`를 JOIN하지 않는다
- **주환님** — `OrderReviewQueryService.findOrderItemSnapshots`. **`findWritableOrderItems`는 *미작성* 항목만 돌려주므로 이미 쓴 후기의 상품명·주문번호를 받을 자리가 없었다.** 조각 1에서 내가 만든 파일에 메서드를 더한 것이라 주환님 파일은 그대로다
- **시은님** — 반대 방향 계약 `ReviewProductQueryService.getPreview`(리뷰 소유). `ProductController.detail`이 이것을 불러 모델에 담는다. **조각 3에서 담당자의 기존 파일을 고친 자리는 여기뿐이다** — 붙이는 방식이 2026-08-06 합의로 정해져 있어 그대로 따랐다(`../specs/review-read.md` B1)

- 상품 상세 후기 미리보기 — 최신 **고정 3개**. **페이징하지 않는다**
- 후기 전체 목록 화면(신규) — **여기만** `PageRequest`/`PageResult` 재사용
- 내 후기 조회는 `GET /mypage/reviews`로 냈다. 주문 상세가 아니라 마이페이지 쪽이고, A1 링크 옆에 나란히 붙는다
- 노출 판단은 `status = 'PUBLISHED'` 하나로 (`docs/community/DOMAIN.md` 4.1 선례)
- **B3의 각 항목은 그 상품의 후기 목록으로 가는 링크다**(`../specs/review-read.md` B3)
- **B3의 수정·삭제 버튼은 조각 4로 미룬다.** A4·A5 경로가 없는 채로 버튼만 띄우면 눌러서 404를 만난다

**검증**: `BLOCKED`·`DELETED` 후기가 목록에 안 나오는지, 페이징 경계(전체 목록·B3), 탈퇴 회원 표시명, **상품 상세 화면 자체가 후기 3개를 렌더링하는지**(`/products/{id}/reviews` 응답만 보면 구멍이 통과한다), 판매 중지 상품과 없는 상품이 **같은 404**인지.
