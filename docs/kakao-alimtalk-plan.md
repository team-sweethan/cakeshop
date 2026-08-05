# Solapi SMS / 카카오 알림톡 외부 메시지 연동 가이드

- **최종 수정일**: 2026-08-04
- **상태**: 구현 및 테스트 완료 (Solapi REST API + Mock Fallback 가상 전송 모드 지원)
- **주요 관련 파일**:
  - `com.cakeshop.global.infra.kakao.SolapiKakaoAlimtalkClient`
  - `com.cakeshop.domain.notification.service.NotificationService`
  - `com.cakeshop.domain.notification.controller.NotificationApiController`
  - `src/main/resources/application.yml`

---

## 1. 개요 및 작동 원리

본 모듈은 알림 생성 시 `DeliveryScope`가 `WEB_AND_SMS`인 경우, 회원 DB에서 수신자 전화번호(`phone`)를 조회하여 실제 스마트폰으로 SMS/LMS 문자를 발송합니다.

- **실제 REST API 전송 모드**: `application.yml`에 `api-key`와 `api-secret` 및 `enabled: true`가 설정되면 Solapi REST API (`POST https://api.solapi.com/messages/v4/send`)를 통해 실제 핸드폰으로 즉시 문자가 발송됩니다.
- **가상 발송 모드 (Mock Mode)**: `enabled: false`이거나 `api-key`가 비어있는 개발/로컬 환경에서는 비용 차감 없이 콘솔 로그로 메시지를 출력하고 DB `notification_deliveries` 테이블에 발송 이력을 기록합니다.
- **개인 프로젝트 최적화**: 사업자 미등록 환경에 맞춰 복잡한 카카오 채널(pfId) 옵션 없이 **Solapi SMS/LMS 문자 발송 전용**으로 간소화되어 안전하게 동작합니다.

---

## 2. 설정 방법 (`application.yml`)

`src/main/resources/application.yml` 파일의 `app.kakao.alimtalk` 블록에 정보를 입력합니다:

```yaml
app:
  kakao:
    alimtalk:
      enabled: true               # true: 실제 발송, false: 가상 콘솔 발송
      api-key: "솔라피_API_KEY"
      api-secret: "솔라피_API_SECRET"
      sender-phone: "01000000000"  # 솔라피에 등록한 본인 발신 휴대폰 번호
```

---

## 3. 알림 발송 연동 방법 (팀원 협업용)

어느 도메인(주문, 결제, 픽업, 게시글 등)에서나 `NotificationService`의 `makeNotification()`을 호출하면서 `deliveryScope(DeliveryScope.WEB_AND_SMS)`를 지정하면 **인앱 알림 저장 + SMS 문자 전송**이 동시에 이루어집니다:

```java
notificationService.makeNotification(NotificationRequest.builder()
    .receiverId(memberId)                     // 수신 회원 ID
    .type(NotificationType.ORDER_PAID)         // 알림 종류 (Enum)
    .args(new Object[]{ "ORD-20260804-001" }) // 문구 치환 변수 (주문번호 등)
    .deliveryScope(DeliveryScope.WEB_AND_SMS) // 👈 웹 알림 + SMS 동시 발송 옵션
    .build());
```

---

## 4. 개발자 문자 발송 테스트 API

개발 중 실제 스마트폰으로 문자가 도착하는지 테스트하기 위한 전용 디버깅 API가 준비되어 있습니다:

- **테스트 URL**: `POST http://localhost:8080/api/notifications/test-sms` (local 프로필 전용)
- **테스트 방법**: 
  1. 웹 사이트에서 전화번호가 등록된 계정으로 로그인 수행 (`local` 환경)
  2. Postman 또는 cURL로 `POST http://localhost:8080/api/notifications/test-sms` 호출 (세션 쿠키 및 `X-CSRF-TOKEN` 헤더 포함)
  3. 로그인 유저의 휴대폰으로 테스트 문자 발송 확인

---

## 5. DB 이력 관리 (`notification_deliveries` 테이블)

알림 전송 시 다음 정보가 `notification_deliveries` 테이블에 자동으로 축적됩니다:
- `notification_id`: 연관 알림 ID (FK)
- `recipient`: 수신자 전화번호
- `template_code`: `DEFAULT_SMS`
- `provider_message_id`: 솔라피 API가 반환한 메시지 그룹 ID (또는 MOCK_ID)
- `status`: `SENT` (성공) / `FAILED` (실패)
- `failure_reason`: 발송 실패 시 상세 예외 메시지
