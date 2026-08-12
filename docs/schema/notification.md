# 알림 스키마

- 담당: 민정
- 테이블: `notifications`, `notification_deliveries`
- 정본: Flyway migration 적용 결과

## `notifications`

수신자별 웹 알림, 관련 도메인 식별자와 읽음 상태를 저장한다.

| 컬럼 | 타입 | 키 | Null | 기본값 | 의미 |
|---|---|---|---|---|---|
| `id` | BIGINT | PK, INDEX | X | AUTO_INCREMENT | 알림 식별자 |
| `receiver_id` | BIGINT | FK, UK, INDEX | X | 없음 | 수신 회원 식별자 |
| `actor_id` | BIGINT | FK | O | NULL | 알림 발생 회원 또는 관리자 식별자 |
| `order_id` | BIGINT | FK | O | NULL | 관련 주문 식별자 |
| `chat_room_id` | BIGINT | FK | O | NULL | 관련 채팅방 식별자 |
| `chat_message_id` | BIGINT | FK | O | NULL | 관련 채팅 메시지 식별자 |
| `post_id` | BIGINT | FK | O | NULL | 관련 게시글 식별자 |
| `comment_id` | BIGINT | FK | O | NULL | 관련 댓글 식별자 |
| `review_id` | BIGINT | FK | O | NULL | 관련 리뷰 식별자 |
| `review_reply_id` | BIGINT | FK | O | NULL | 관련 리뷰 답글 식별자 |
| `user_coupon_id` | BIGINT | FK | O | NULL | 관련 회원 쿠폰 식별자 |
| `notification_type` | VARCHAR(50) |  | X | 없음 | 알림 유형 |
| `title` | VARCHAR(200) |  | X | 없음 | 제목 |
| `content` | TEXT |  | X | 없음 | 내용 |
| `delivery_scope` | VARCHAR(30) |  | X | `'WEB_ONLY'` | 발송 범위 |
| `is_read` | TINYINT(1) | INDEX | X | `0` | 읽음 여부 |
| `read_at` | DATETIME(6) |  | O | NULL | 읽은 시각 |
| `event_key` | VARCHAR(100) | UK | X | 없음 | 수신자별 이벤트 중복 방지 키 |
| `created_at` | DATETIME(6) |  | X | `CURRENT_TIMESTAMP(6)` | 최초 생성 시각 |
| `last_event_at` | DATETIME(6) | INDEX | X | `CURRENT_TIMESTAMP(6)` | 가장 최근 관련 이벤트 시각 |

- UK: `uk_notifications_receiver_event` (`receiver_id`, `event_key`)
- FK 삭제 정책: `receiver_id`는 `members.id`와 `ON DELETE CASCADE`
- FK 삭제 정책: 나머지 관련 식별자는 각 대상 테이블과 `ON DELETE SET NULL`
- INDEX: `idx_notifications_receiver_last_event` (`receiver_id`, `last_event_at`, `id`)
- INDEX: `idx_notifications_receiver_read_last_event` (`receiver_id`, `is_read`, `last_event_at`, `id`)

## `notification_deliveries`

알림의 외부 메시지 발송 상태와 제공자 응답을 저장한다.

| 컬럼 | 타입 | 키 | Null | 기본값 | 의미 |
|---|---|---|---|---|---|
| `id` | BIGINT | PK | X | AUTO_INCREMENT | 발송 이력 식별자 |
| `notification_id` | BIGINT | FK | X | 없음 | 알림 식별자 |
| `recipient` | VARCHAR(500) |  | X | 없음 | 수신 전화번호 |
| `template_code` | VARCHAR(100) |  | X | 없음 | 메시지 템플릿 코드 |
| `provider_message_id` | VARCHAR(200) | INDEX | O | NULL | 발송 제공자 메시지 식별자 |
| `status` | VARCHAR(30) | INDEX | X | `'PENDING'` | 발송 상태 |
| `failure_reason` | TEXT |  | O | NULL | 실패 사유 |
| `sent_at` | DATETIME |  | O | NULL | 발송 완료 시각 |
| `delivered_at` | DATETIME(6) |  | O | NULL | 전달 완료 시각 |
| `created_at` | DATETIME(6) | INDEX | X | `CURRENT_TIMESTAMP(6)` | 생성 시각 |
| `updated_at` | DATETIME(6) |  | X | `CURRENT_TIMESTAMP(6)` | 수정 시각, 수정 시 자동 갱신 |

- FK: `fk_deliveries_notification` — `notification_id` → `notifications.id`, `ON DELETE CASCADE`
- INDEX: `idx_deliveries_provider_msg_id` (`provider_message_id`)
- INDEX: `idx_deliveries_status_created` (`status`, `created_at DESC`)

## 관련 migration

- `V0__initial_schema.sql`
- `V20260804_150010__add_notification_tables.sql`
- `V20260806_162203__add_notification_last_event_at.sql`
