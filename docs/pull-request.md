# cakeshop PR 작성 규칙

- **범위**: GitHub Pull Request 작성, 리뷰 요청, 병합 기준
- **PR 본문 양식**: [GitHub PR 템플릿](../.github/pull_request_template.md)
- **관련 문서**: [코드 컨벤션](conventions.md), [테스트 작성 규칙](testing.md)

이 문서는 PR 정책과 병합 기준을 정한다. PR 본문의 항목과 체크리스트는 GitHub PR 템플릿을 정본으로 사용한다.

## 1. 기본 원칙

- 하나의 PR은 하나의 목적만 다룬다. 기능 변경과 관련 없는 패키지 이동, 이름 변경, 포맷 정리는 분리한다.
- 다른 담당자의 코드나 테스트를 수정해야 한다면 먼저 담당자와 수정 범위를 합의하고 PR 본문에 기록한다.
- 리뷰 가능한 상태가 아니거나 설계 논의가 필요하면 Draft PR로 생성한다.
- PR 작성자는 리뷰 요청 전에 변경 파일을 직접 확인하고 불필요한 파일, 비밀 정보, 디버그 코드를 제거한다.
- AI 코드 리뷰는 보조 수단이며 CI 통과와 사람의 승인을 대신하지 않는다.

## 2. PR 제목

제목은 `<type>: 변경 요약` 형식으로 작성한다. 제목만 읽어도 변경 결과를 알 수 있게 작성하고 마침표는 붙이지 않는다.

| type | 사용 기준 |
|---|---|
| `feat` | 사용자에게 제공되는 기능 추가 |
| `fix` | 버그 수정 |
| `refactor` | 동작 변경 없는 구조 개선 |
| `test` | 테스트 추가·수정 |
| `docs` | 문서만 변경 |
| `ci` | CI workflow 변경 |
| `chore` | 빌드, 설정, 개발 환경 정비 |

```text
feat: 관리자 상품 재고 수정 기능 추가
fix: 결제 취소 시 상품 재고 복구 누락 수정
ci: PR Gradle 테스트 workflow 추가
```

## 3. PR 본문 작성

- GitHub가 자동으로 불러오는 [PR 템플릿](../.github/pull_request_template.md)의 항목을 삭제하지 않고 작성한다.
- 실행한 테스트 명령과 결과를 기록한다. 실행하지 못한 테스트는 사유와 후속 계획을 남긴다.
- DB 변경이 있으면 새 migration 파일명, 기존 데이터 영향, 적용·복구 방법을 기록한다.
- 공유된 versioned migration은 수정하지 않고 `gradlew newMigration -Pdesc=<snake_case>`로 새 파일을 만든다.
- 로컬 seed 변경 시 팀원이 다시 실행해야 하는지 명시한다. `rds` 반영은 별도 검토와 승인을 거친다.
- 해당하지 않는 체크리스트 항목은 사유를 남긴다.

## 4. 리뷰 요청과 병합 기준

리뷰 요청 전 다음 조건을 만족해야 한다.

1. PR 템플릿의 필수 항목을 모두 작성한다.
2. 자신의 변경 사항을 self-review한다.
3. 변경한 동작을 검증하는 테스트를 추가하거나 미추가 사유를 기록한다.
4. 담당자가 다른 코드의 수정 범위를 합의한다.
5. CI 통과를 확인한다.
6. Draft 상태를 해제해 리뷰 가능한 상태로 전환하고 Codex 자동 리뷰가 시작되는지 확인한다.
7. Codex 자동 리뷰의 P0/P1 항목을 해결하거나 처리 근거를 남긴다.

병합 전에는 전체 CI 성공과 미해결 review conversation 정리를 원칙으로 한다. 긴급하게 예외 병합이 필요하면 사유와 후속 조치 이슈를 남기고 팀의 승인을 받는다.

**승인은 `main` 병합에만 요구한다.** `dev`는 조각 단위로 하루에 여러 번 오가는 통합 브랜치라, 승인을 필수로 걸면 6명이 서로를 기다린다. 릴리스로 나가는 `main`은 되돌리는 비용이 다르므로 승인을 받는다.

**`dev`라도 리뷰어 지정을 건너뛰지 않는다.** 담당 외 도메인에 파일을 만든 PR은 그 도메인 담당자를 리뷰어로 지정한다 — 승인이 필수가 아닌 것과 확인을 받지 않는 것은 다르다([`conventions.md` 15.8](conventions.md#15-도메인-간-연동)).

### branch protection — 무엇이 강제되고 무엇이 원칙인가

**강제되지 않는 것을 강제된다고 믿는 쪽이 더 위험하다.** 아래가 실제 설정이다(2026-08-08).

| | `dev` | `main` |
|---|---|---|
| required check | `Test` · `Migration immutability` | **없음** (아래 공백) |
| 승인 필수 인원 | **없음** | **2명** |
| review conversation 정리 | 원칙 | **강제** |
| strict (base 최신화 요구) | **끔** | 끔 |
| force push · 브랜치 삭제 | 금지 | 금지 |
| 브랜치 잠금 | — | **잠김**(`lock_branch`) |
| 관리자 우회 | 허용 | 허용 |

`dev`의 strict를 끈 이유: 6명이 동시에 머지하는 동안 누가 하나 머지할 때마다 나머지 PR이 전부 out of date가 되어, 갱신과 CI 재실행이 머지 속도를 따라가지 못한다. 합쳐서 깨지는 조합은 `dev` push에도 도는 CI가 잡는다.

`main`은 잠겨 있어 평소에는 아무도 못 민다. 릴리스 시점에 관리자가 풀고 머지한다.

**알려진 공백: `main`에 required check가 없다.** 잠금과 승인 2명으로 막혀 있지만 CI 초록불은 기계가 요구하지 않는다. 릴리스 절차를 정할 때 함께 정한다.

**required check 이름은 `ci.yml`의 `name:` 값이다.** job 이름을 바꾸면 그 이름의 검사가 영영 오지 않아 **모든 PR이 조용히 머지 불가**가 된다. 워크플로의 `name:`을 고칠 때는 이 표도 함께 고친다.

### Codex 자동 리뷰 설정과 운영

- 저장소 관리자는 [Codex Code Review 설정](https://chatgpt.com/codex/settings/code-review)에서 `team-sweethan/cakeshop`의 **Code review**와 **Automatic reviews**를 활성화한다.
- 리뷰는 연결된 ChatGPT/Codex 요금제 사용량으로 실행한다. 별도의 OpenAI API 키, GitHub Secret, 코드 리뷰용 GitHub Actions workflow를 추가하지 않는다.
- Codex는 루트 `AGENTS.md`의 `## Code Review Rules`를 저장소 전역 리뷰 기준으로 사용한다.
- 자동 리뷰는 PR을 Draft에서 Ready for review 상태로 전환한 뒤 시작되는지 확인한다.
- 자동 리뷰가 시작되지 않으면 PR 댓글에 정확히 `@codex review`를 남기고 저장소 연결 및 Automatic reviews 설정을 확인한다.
- Codex 리뷰는 추가 검토이며 CI, branch protection, 사람의 필수 승인을 대체하지 않는다.
