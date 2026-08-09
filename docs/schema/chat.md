# 채팅 스키마

- 담당: 민정
- 테이블: `chat_rooms`, `chat_room_orders`, `chat_messages`, `chat_message_reads`
- 정본: Flyway migration 적용 결과

## `chat_rooms`

고객과 관리자의 채팅방을 저장한다.

| 컬럼 | 타입 | 키 | Null | 기본값 | 의미 |
|---|---|---|---|---|---|
| `id` | BIGINT | PK | X | AUTO_INCREMENT | 채팅방 식별자 |
| `customer_id` | BIGINT | FK, UK | X | 없음 | 고객 회원 식별자 |
| `admin_id` | BIGINT | FK | O | NULL | 담당 관리자 식별자 |
| `status` | VARCHAR(30) |  | X | `'OPEN'` | 채팅방 상태 |
| `last_message_at` | DATETIME(6) |  | O | NULL | 마지막 메시지 시각 |
| `created_at` | DATETIME(6) |  | X | `CURRENT_TIMESTAMP(6)` | 생성 시각 |

- UK: `uk_chat_rooms_customer` (`customer_id`)
- FK: `customer_id`, `admin_id` → 각각 `members.id`

## `chat_room_orders`

채팅방에서 다루는 주문을 연결한다.

| 컬럼 | 타입 | 키 | Null | 기본값 | 의미 |
|---|---|---|---|---|---|
| `id` | BIGINT | PK | X | AUTO_INCREMENT | 연결 식별자 |
| `chat_room_id` | BIGINT | FK | X | 없음 | 채팅방 식별자 |
| `order_id` | BIGINT | FK, UK | X | 없음 | 주문 식별자 |
| `created_at` | DATETIME(6) |  | X | `CURRENT_TIMESTAMP(6)` | 생성 시각 |

- UK: `uk_chat_room_orders_order` (`order_id`)
- FK: `chat_room_id` → `chat_rooms.id`, `order_id` → `orders.id`

## `chat_messages`

채팅방의 메시지 본문 또는 이미지를 저장한다.

| 컬럼 | 타입 | 키 | Null | 기본값 | 의미 |
|---|---|---|---|---|---|
| `id` | BIGINT | PK | X | AUTO_INCREMENT | 메시지 식별자 |
| `chat_room_id` | BIGINT | FK | X | 없음 | 채팅방 식별자 |
| `sender_id` | BIGINT | FK | X | 없음 | 발신 회원 식별자 |
| `message_type` | VARCHAR(30) |  | X | 없음 | 메시지 유형 |
| `content` | TEXT |  | O | NULL | 메시지 본문 |
| `image_url` | VARCHAR(500) |  | O | NULL | 이미지 URL |
| `created_at` | DATETIME(6) |  | X | `CURRENT_TIMESTAMP(6)` | 생성 시각 |

- FK: `chat_room_id` → `chat_rooms.id`, `sender_id` → `members.id`

## `chat_message_reads`

회원별 메시지 읽음 이력을 저장한다.

| 컬럼 | 타입 | 키 | Null | 기본값 | 의미 |
|---|---|---|---|---|---|
| `id` | BIGINT | PK | X | AUTO_INCREMENT | 읽음 이력 식별자 |
| `chat_message_id` | BIGINT | FK, UK | X | 없음 | 메시지 식별자 |
| `member_id` | BIGINT | FK, UK | X | 없음 | 읽은 회원 식별자 |
| `read_at` | DATETIME(6) |  | X | `CURRENT_TIMESTAMP(6)` | 읽은 시각 |

- UK: `uk_chat_message_reads_message_member` (`chat_message_id`, `member_id`)
- FK: `chat_message_id` → `chat_messages.id`, `member_id` → `members.id`

## 관련 migration

- `V0__initial_schema.sql`
