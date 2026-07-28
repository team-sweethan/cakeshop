# cakeshop Codex 안내

- 이 프로젝트는 Java 21, Spring Boot, Spring MVC, Spring Security, MyBatis, MariaDB를 사용한다.
- 구현과 리뷰 시 `docs/conventions.md`, `docs/testing.md`, `docs/pull-request.md`를 정본으로 따른다.
- 전체 검증은 Windows에서 `.\gradlew.bat test`, macOS·Linux·CI에서 `./gradlew test`로 실행한다.
- 포맷, 컴파일, 테스트처럼 결정적인 검사는 CI에 맡기고 코드 리뷰는 아래의 중대한 위험에 집중한다.

## Code Review Rules

### 인증·인가와 정보 노출

- 회원 소유 자원은 요청으로 전달된 회원 ID를 신뢰하지 말고 인증된 사용자와 자원 소유권을 Service에서 검증한다. 관리자 기능은 화면 요소를 숨기는 데 의존하지 말고 Spring Security에서 `ADMIN` 권한을 강제한다.
- 비밀번호, 인증 정보, 개인정보, SQL, 내부 경로를 응답이나 로그에 노출하지 않는다. 안전한 경로는 사용자용 오류와 내부 진단 정보를 분리하는 것이다.

### 트랜잭션과 상태 무결성

- 주문·결제·재고·쿠폰처럼 여러 쓰기와 상태 전이가 연결된 작업은 public Service 메서드의 단일 트랜잭션에서 현재 상태와 목표 상태를 검증한다. 실패 시 일부 변경만 남거나 재시도·웹훅으로 중복 반영되지 않아야 한다.
- 안전한 경로는 상태 전이를 도메인 규칙으로 검증하고, 외부 요청의 고유 키나 처리 이력으로 중복 실행을 차단하며, rollback과 금지된 전이를 테스트하는 것이다.

### SQL과 스키마 안전성

- MyBatis에서 사용자 제어 값은 `#{}`로 바인딩하고 `${}` 치환을 사용하지 않는다. 정렬처럼 동적 SQL이 필요하면 허용된 enum 값을 `<choose>`로 매핑한다.
- 공유된 Flyway migration은 수정하지 않고 새 versioned migration을 추가한다. 스키마 변경은 기존 데이터 영향과 복구 방법을 설명하고 MariaDB Testcontainers 테스트로 검증한다.
