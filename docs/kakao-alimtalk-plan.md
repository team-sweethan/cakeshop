# 카카오 알림톡 / SMS API 연동 가이드

- **작성일**: 2026-07-30
- **상태**: 구현 완료 (Solapi REST API + Mock Fallback)
- **주요 관련 파일**:
  - `com.cakeshop.global.infra.kakao.SolapiKakaoAlimtalkClient`
  - `com.cakeshop.domain.notification.service.NotificationService`
  - `src/main/resources/application.yml`

---

## 1. 개요 및 작동 원리

본 모듈은 알림 생성 시 `DeliveryScope` 가 `WEB_AND_SMS` 인 경우, 회원 DB에서 수신자 전화번호(`phone`)를 조회하여 카카오 알림톡 및 SMS 문자를 실제 스마트폰으로 발송합니다.

- **실시간 REST API 전송 모드**: `application.yml` 에 `api-key` 와 `api-secret` 이 등록되면 Solapi REST API (`POST https://api.solapi.com/messages/v4/send`) 를 통해 실제 스마트폰으로 즉시 전송됩니다.
- **가상 발송 모드 (Mock Mode)**: `api-key` 가 비어있는 개발/로컬 환경에서는 에러 없이 콘솔 로그로 메시지를 찍고 DB `notification_deliveries` 테이블에 발송 이력을 기록합니다.

---

## 2. 설정 방법 (`application.yml`)

`src/main/resources/application.yml` 파일의 `app.kakao.alimtalk` 블록에 아래 정보를 입력합니다:

```yaml
app:
  kakao:
    alimtalk:
      enabled: true
      api-key: "발급받은_API_KEY"
      api-secret: "발급받은_API_SECRET"
      sender-phone: "01012345678"  # 솔라피에 등록한 본인 발신 휴대폰 번호
      pf-id: ""                   # (선택) 카카오톡 채널 발신프로필 ID
```

---

## 3. 알림 발송 예시 코드

어느 도메인(주문, 결제, 게시글 등)에서나 `NotificationService` 의 `makeNotification()` 을 호출하면서 `deliveryScope(DeliveryScope.BOTH)` 를 지정하면 웹 알림함 저장 + 카카오톡/SMS 전송이 동시에 이루어집니다:

```java
notificationService.makeNotification(NotificationRequest.builder()
    .receiverId(memberId)             // 수신 회원 ID
    .type(NotificationType.ORDER_PAID) // 알림 종류
    .args(new Object[]{ "ORD-001" })   // 템플릿 치환 변수
    .deliveryScope(DeliveryScope.BOTH) // 👈 WEB_ONLY 대신 BOTH 지정!
    .build());
```

---

## 4. DB 이력 관리 (`notification_deliveries` 테이블)

알림 전송 시 다음 정보가 `notification_deliveries` 테이블에 자동으로 축적됩니다:
- `notification_id`: 연관 알림 ID
- `channel`: `KAKAO_ALIMTALK`
- `recipient`: 수신자 전화번호
- `provider_message_id`: 솔라피 API가 반환한 메시지 그룹 ID (또는 MOCK_ID)
- `status`: `SENT` (성공) / `FAILED` (실패)
