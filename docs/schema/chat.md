# 채팅 스키마

- 담당: 민정
- 테이블: `chat_rooms`, `chat_room_orders`, `chat_messages`, `chat_message_attachments`, `chat_room_read_cursors`, `customer_admin_notes`
- 정본: Flyway migration 적용 결과 (`V20260807_112117__align_chat_schema.sql`)

## `chat_rooms`

고객과 관리자의 1:1 채팅방을 저장한다.

| 컬럼 | 타입 | 키 | Null | 기본값 | 의미 |
|---|---|---|---|---|---|
| `id` | BIGINT | PK | X | AUTO_INCREMENT | 채팅방 식별자 |
| `customer_id` | BIGINT | FK, UK | X | 없음 | 고객 회원 식별자 |
| `status` | VARCHAR(30) |  | X | `'OPEN'` | 방 업무 상태 (`OPEN`, `CLOSED`) |
| `response_status` | VARCHAR(30) |  | X | 없음 | 답변 상태 (`WAITING_ADMIN`, `WAITING_CUSTOMER`, `RESOLVED`) |
| `last_message_id` | BIGINT | FK | O | NULL | 최신 메시지 식별자 |
| `last_message_at` | DATETIME(6) |  | O | NULL | 최신 메시지 생성 시각 |
| `created_at` | DATETIME(6) |  | X | `CURRENT_TIMESTAMP(6)` | 생성 시각 |
| `updated_at` | DATETIME(6) |  | X | `CURRENT_TIMESTAMP(6)` | 최종 수정 시각 |

- UK: `uk_chat_rooms_customer` (`customer_id`)
- FK: `customer_id` → `members.id`, `last_message_id` → `chat_messages.id`

## `chat_room_orders`

채팅방에서 연동된 주문 내역 및 대화 앵커 위치를 저장한다.

| 컬럼 | 타입 | 키 | Null | 기본값 | 의미 |
|---|---|---|---|---|---|
| `id` | BIGINT | PK | X | AUTO_INCREMENT | 연결 식별자 |
| `chat_room_id` | BIGINT | FK | X | 없음 | 채팅방 식별자 |
| `order_id` | BIGINT | FK, UK | X | 없음 | 주문 식별자 |
| `conversation_anchor_message_id` | BIGINT | FK | O | NULL | 연동 대화 위치 앵커 메시지 ID |
| `created_at` | DATETIME(6) |  | X | `CURRENT_TIMESTAMP(6)` | 생성 시각 |

- UK: `uk_chat_room_orders_order` (`order_id`)
- FK: `chat_room_id` → `chat_rooms.id`, `order_id` → `orders.id`, `conversation_anchor_message_id` → `chat_messages.id`

## `chat_messages`

채팅방의 대화 메시지를 저장한다.

| 컬럼 | 타입 | 키 | Null | 기본값 | 의미 |
|---|---|---|---|---|---|
| `id` | BIGINT | PK | X | AUTO_INCREMENT | 메시지 식별자 |
| `chat_room_id` | BIGINT | FK | X | 없음 | 채팅방 식별자 |
| `sender_id` | BIGINT | FK | X | 없음 | 발신 회원 식별자 |
| `product_id` | BIGINT | FK | O | NULL | 문의 상품 식별자 (선택) |
| `content` | TEXT |  | O | NULL | 메시지 본문 |
| `created_at` | DATETIME(6) |  | X | `CURRENT_TIMESTAMP(6)` | 생성 시각 |

- FK: `chat_room_id` → `chat_rooms.id`, `sender_id` → `members.id`, `product_id` → `products.id`

## `chat_message_attachments`

메시지에 첨부된 S3 이미지 파일의 메타데이터 및 Object Key를 저장한다.

| 컬럼 | 타입 | 키 | Null | 기본값 | 의미 |
|---|---|---|---|---|---|
| `id` | BIGINT | PK | X | AUTO_INCREMENT | 첨부파일 식별자 |
| `chat_message_id` | BIGINT | FK | X | 없음 | 메시지 식별자 |
| `object_key` | VARCHAR(500) |  | X | 없음 | S3 객체 키 |
| `original_filename` | VARCHAR(255) |  | X | 없음 | 원본 파일명 |
| `content_type` | VARCHAR(100) |  | X | 없음 | MIME 타입 |
| `file_size` | BIGINT |  | X | 없음 | 파일 크기 (Byte) |
| `display_order` | INT |  | X | `0` | 정렬 순서 |
| `created_at` | DATETIME(6) |  | X | `CURRENT_TIMESTAMP(6)` | 생성 시각 |

- FK: `chat_message_id` → `chat_messages.id`

## `chat_room_read_cursors`

고객/관리자 주체별 채팅방 읽음 위치 커서를 저장한다.

| 컬럼 | 타입 | 키 | Null | 기본값 | 의미 |
|---|---|---|---|---|---|
| `id` | BIGINT | PK | X | AUTO_INCREMENT | 커서 식별자 |
| `chat_room_id` | BIGINT | FK, UK | X | 없음 | 채팅방 식별자 |
| `reader_side` | VARCHAR(30) | UK | X | 없음 | 읽은 주체 (`CUSTOMER`, `ADMIN`) |
| `last_read_message_id` | BIGINT | FK | O | NULL | 읽은 마지막 메시지 ID |
| `last_read_at` | DATETIME(6) |  | O | NULL | 마지막 읽음 처리 시각 |
| `created_at` | DATETIME(6) |  | X | `CURRENT_TIMESTAMP(6)` | 생성 시각 |
| `updated_at` | DATETIME(6) |  | X | `CURRENT_TIMESTAMP(6)` | 수정 시각 |

- UK: `uk_chat_room_read_cursor` (`chat_room_id`, `reader_side`)
- FK: `chat_room_id` → `chat_rooms.id`, `last_read_message_id` → `chat_messages.id`

## `customer_admin_notes`

고객별 관리자 특이사항 메모를 저장한다.

| 컬럼 | 타입 | 키 | Null | 기본값 | 의미 |
|---|---|---|---|---|---|
| `id` | BIGINT | PK | X | AUTO_INCREMENT | 메모 식별자 |
| `customer_id` | BIGINT | FK, UK | X | 없음 | 메모 대상 고객 회원 ID |
| `content` | TEXT |  | X | 없음 | 메모 내용 |
| `updated_by` | BIGINT | FK | X | 없음 | 마지막 수정 관리자 ID |
| `created_at` | DATETIME(6) |  | X | `CURRENT_TIMESTAMP(6)` | 생성 시각 |
| `updated_at` | DATETIME(6) |  | X | `CURRENT_TIMESTAMP(6)` | 수정 시각 |

- UK: `uk_customer_admin_notes_customer` (`customer_id`)
- FK: `customer_id` → `members.id`, `updated_by` → `members.id`

## 관련 migration

- `V20260807_112117__align_chat_schema.sql`

