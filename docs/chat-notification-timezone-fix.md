# 채팅·알림 한국 시간 기준 수정

기준: 기존 `team-sweethan/cakeshop` dev `084c6f2f`, 2026-11-05.

## 범위

- ChatService, NotificationService, NotificationDeliveryService는 서버 기본 시간대 대신 기존 서울 Clock으로 현재 시각을 생성한다.
- 고객·관리자 채팅과 알림 화면은 공용 `korea-time.js`를 사용한다. offset 없는 LocalDateTime은 한국 시각으로 해석하고, Z/offset이 있는 값은 같은 순간의 한국 시각으로 표시한다.
- 관리자 채팅의 시각 비교도 같은 파서를 사용한다. 메시지 ID 기반 비교와 그 밖의 업무 규칙은 유지한다.
- 고객·관리자 알림 목록은 기존대로 lastEventAt을 우선하되 날짜 문자열을 단순 자르지 않는다.

## 결제와 분리

결제 완료 알림은 OrderNotificationSender가 NotificationService를 호출하고, 알림 시각은 NotificationService에서 생성한다. 토스 approvedAt/canceledAt을 알림 생성 시각으로 전달하지 않으므로 이 수정에 결제 클라이언트 변경은 필요하지 않다.

TossPaymentClient의 OffsetDateTime 변환 개선은 결제 담당 검토가 필요한 별도 작업이다. 이번에는 결제 코드·승인/취소·금액·상태를 바꾸지 않는다.

## 배포와 기존 데이터

- 재배포 후 새 기록에 적용한다. 기존 DATETIME 행을 보정하거나 일괄 9시간 더하지 않는다.
- DB·Flyway·seed·JVM 전역 시간대·인증·알림 발송 조건/간격·빌드 설정은 변경하지 않는다.
- 저장소의 local/rds JDBC 세션 설정은 기존 +09:00을 유지한다. AWS에서 datasource URL을 환경변수로 덮어썼다면 실제 세션 시간대는 별도 확인한다.
- `cakeshop_refactoring` 폴더는 수정하지 않는다. 이 작업은 별도 `cakeshop-timezone-fix` 복제본에만 보관한다.

## 검증

- Node 시간 표시·네 화면 연결 검사 5개: UTC/Asia/Seoul/America/Los_Angeles, 자정 날짜 경계, offset 없는 값과 Z/offset 값의 이중 보정 방지.
- UTC JVM에서 NotificationTimeTests 2개·ChatServiceTimeTests 1개 통과.
- 기존 전체 `unitTest`도 UTC JVM에서 통과했다. 별도 JavaScript 검사 5개와 구분한다.
- 한글 작업 경로에서 Gradle 테스트 클래스 로딩이 실패해 로컬 전용 init script로 빌드 산출물만 영문 경로로 분리했다. 저장소 build.gradle은 수정하지 않았다.
- 실제 AWS 배포·기존 DB 보정·브라우저 주문/채팅 왕복·Docker MariaDB 통합 검증은 아직 수행하지 않았다.

PR 생성·dev 병합·운영 배포는 이 로컬 수정과 별도다.
