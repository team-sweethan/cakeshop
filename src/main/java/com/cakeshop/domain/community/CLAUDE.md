# Community 도메인 작업 규칙

이 디렉터리(`domain/community/`)와 아래 관련 파일을 수정할 때는 **반드시 다음 두 문서를 먼저 읽고 그에 따른다.**

- **`docs/community/DOMAIN.md`** — 도메인 규칙의 정본. 상태 모델, 권한, 기능별 규칙, 입력 검증. 여기 적힌 것과 다르게 구현하지 않는다.
- **`docs/community/PLAN.md`** — 작업 순서, 진행 상태, 하네스, 위험, 결정 로그. 지금 어느 조각을 하는지 여기서 확인한다.

템플릿을 만질 때는 여기까지 함께 읽는다.

- **`docs/community/SCREENS.md`** — 화면 명세의 인덱스이자 파일 형식 규약. 화면 목록과 하네스가 무엇을 보증하고 무엇을 보증하지 않는지가 여기 있다.
- **`docs/community/screens/*.md`** — 화면 구성의 정본. **화면 하나에 파일 하나**이고, 주소·모델·문구·조건부 노출을 담는다. **문구를 바꾸거나 화면을 추가하면 해당 파일도 함께 고친다** — `CommunityScreenDocTests`가 이 파일들과 템플릿을 대조하므로 안 고치면 빌드가 깨진다.

프로젝트 전체 규칙은 저장소 루트 `AGENTS.md`가 상위 정본이며, 충돌하면 `AGENTS.md`가 우선한다.

## 이 도메인의 파일 범위

- `src/main/java/com/cakeshop/domain/community/**`
- `src/main/resources/mapper/community/CommunityMapper.xml`
- `src/main/resources/db/seed/seed-community.sql` (로컬 샘플 데이터. `seed-local.sql` 다음에 실행한다)
- `src/main/resources/templates/customer/community/**`
- `src/main/resources/templates/admin/community/**`
- `src/test/java/com/cakeshop/domain/community/**`

## 작업 절차

1. `docs/community/DOMAIN.md`와 `docs/community/PLAN.md`를 읽는다.
2. `PLAN.md`의 조각 순서를 확인하고, **현재 조각의 범위를 벗어나는 구현을 하지 않는다.**
3. 구현 후 `./gradlew clean test`를 실행한다.
4. 해당 조각에 명시된 검증 항목을 확인한다.
5. 변경 파일, 실행한 검증, 남은 위험을 보고한다.

## 규칙을 벗어나야 할 때

DOMAIN.md의 결정이 잘못됐거나 부족하다고 판단되면 **코드로 우회하지 말고 먼저 알린다.** 규칙과 코드가 어긋나면 다음 작업자(사람이든 AI든)가 어느 쪽을 믿어야 할지 알 수 없게 된다. 결정을 바꾸면 DOMAIN.md를 고치고 PLAN.md 결정 로그에 한 줄 남긴다.

DOMAIN.md 9절의 **보류 항목**은 아직 결정되지 않은 것이다. 임의로 정하지 말고 해당 조각 차례에 확인을 받는다.

## 특히 놓치기 쉬운 것

아래는 결과가 겉보기에 정상이라 리뷰에서 놓치기 쉬운 항목이다. 근거는 DOMAIN.md의 해당 절에 있다.

- **목록의 댓글 수는 스칼라 서브쿼리로 집계한다.** `LEFT JOIN comments ... GROUP BY`로 바꾸지 않는다 — 결과는 같지만 `LIMIT`이 `GROUP BY` 이후에 적용되어 전체 스캔이 된다. (6.1)
- **노출 여부는 `posts.status`만으로 판단한다.** `blocked_at IS NULL` 조건을 추가하지 않는다. (4.1)
- **`like_count`는 증분하지 않고 매번 재계산한다.** (6.5)
- **좋아요는 토글이 아니라 POST/DELETE 분리이며 둘 다 멱등하다.** 신고는 반대로 중복 시 에러를 낸다. (6.5, 6.6)
- **댓글·좋아요·신고는 대상 게시글이 `PUBLISHED`인지 Service에서 검증한다.** 게시글 soft delete 시 자식 데이터는 그대로 남기 때문이다. (4.5)
- **`parent_comment_id`를 엔티티·DTO·SQL 어디에도 등장시키지 않는다.** 1차에 대댓글은 없다. DB 제약도 추가하지 않는다. (6.4)
- **삭제된 댓글은 자리 표시로 남기되 개수 집계에서는 제외한다.** (4.4)
- **템플릿에서 `th:utext`를 쓰지 않는다.** 본문은 순수 텍스트이며 줄바꿈은 CSS로 처리한다. (7)
- **조건부로만 그려지는 블록은 렌더링 테스트로 고정한다.** 차단 안내, 빈 목록, 쪽 이동처럼 평소 화면에 없는 것은 표현식이 깨져도 아무도 모른 채 지나간다. (`SCREENS.md`)
- **`V0__initial_schema.sql`을 비롯한 공유 migration을 수정하지 않는다.** 새 versioned migration을 `gradlew newMigration -Pdesc=<snake_case>`로 만든다.
