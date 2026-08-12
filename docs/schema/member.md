# 회원 스키마

- 담당: 수민
- 테이블: `members`, `social_accounts`, `member_status_histories`, `email_verifications`
- 정본: Flyway migration 적용 결과

## `members`

회원의 인증 정보, 프로필과 상태를 저장한다.

| 컬럼 | 타입 | 키 | Null | 기본값 | 의미 |
|---|---|---|---|---|---|
| `id` | BIGINT | PK | X | AUTO_INCREMENT | 회원 식별자 |
| `email` | VARCHAR(255) | UK | X | 없음 | 로그인 이메일 |
| `password` | VARCHAR(255) |  | O | NULL | 암호화된 비밀번호 |
| `name` | VARCHAR(50) |  | X | 없음 | 회원 이름 |
| `nickname` | VARCHAR(50) |  | X | 없음 | 표시 이름 |
| `phone` | VARCHAR(30) |  | O | NULL | 전화번호 |
| `role` | VARCHAR(30) |  | X | `'USER'` | 권한 |
| `status` | VARCHAR(20) |  | X | `'ACTIVE'` | 회원 상태 |
| `suspended_at` | DATETIME(6) |  | O | NULL | 정지 시각 |
| `suspended_reason` | VARCHAR(500) |  | O | NULL | 정지 사유 |
| `created_at` | DATETIME(6) |  | X | `CURRENT_TIMESTAMP(6)` | 생성 시각 |
| `updated_at` | DATETIME(6) |  | X | `CURRENT_TIMESTAMP(6)` | 수정 시각, 수정 시 자동 갱신 |
| `withdrawn_at` | DATETIME(6) |  | O | NULL | 탈퇴 시각 |
| `birth_date` | DATE |  | O | NULL | 생년월일 |

- UK: `uk_members_email` (`email`)
- CHECK: `chk_members_status` — `status IN ('ACTIVE', 'SUSPENDED', 'WITHDRAWN')`

## `social_accounts`

회원과 소셜 로그인 계정을 연결한다.

| 컬럼 | 타입 | 키 | Null | 기본값 | 의미 |
|---|---|---|---|---|---|
| `id` | BIGINT | PK | X | AUTO_INCREMENT | 소셜 계정 식별자 |
| `member_id` | BIGINT | FK | X | 없음 | 회원 식별자 |
| `provider` | VARCHAR(30) | UK | X | 없음 | 소셜 로그인 제공자 |
| `provider_id` | VARCHAR(100) | UK | X | 없음 | 제공자 측 계정 식별자 |
| `social_email` | VARCHAR(255) |  | O | NULL | 제공자 계정 이메일 |
| `created_at` | DATETIME(6) |  | X | `CURRENT_TIMESTAMP(6)` | 생성 시각 |
| `updated_at` | DATETIME(6) |  | X | `CURRENT_TIMESTAMP(6)` | 수정 시각, 수정 시 자동 갱신 |

- UK: `uk_social_accounts_provider_id` (`provider`, `provider_id`)
- FK: `fk_social_accounts_member` — `member_id` → `members.id`

## `member_status_histories`

회원 정지·활성화 처리 이력을 저장한다.

| 컬럼 | 타입 | 키 | Null | 기본값 | 의미 |
|---|---|---|---|---|---|
| `id` | BIGINT | PK, INDEX | X | AUTO_INCREMENT | 상태 이력 식별자 |
| `member_id` | BIGINT | FK, INDEX | X | 없음 | 대상 회원 식별자 |
| `action` | VARCHAR(20) |  | X | 없음 | 처리 동작 |
| `before_status` | VARCHAR(20) |  | X | 없음 | 처리 전 상태 |
| `after_status` | VARCHAR(20) |  | X | 없음 | 처리 후 상태 |
| `reason` | VARCHAR(500) |  | X | 없음 | 처리 사유 |
| `processed_by` | BIGINT | FK | O | NULL | 처리 회원 또는 관리자 식별자 |
| `processed_at` | DATETIME(6) | INDEX | X | `CURRENT_TIMESTAMP(6)` | 처리 시각 |

- FK: `fk_member_status_histories_member` — `member_id` → `members.id`
- FK: `fk_member_status_histories_processor` — `processed_by` → `members.id`
- CHECK: `action IN ('SUSPEND', 'ACTIVATE')`
- CHECK: `before_status`, `after_status` 각각 `IN ('ACTIVE', 'SUSPENDED')`
- INDEX: `idx_member_status_histories_member_processed` (`member_id`, `processed_at DESC`, `id DESC`)

## `email_verifications`

회원가입과 비밀번호 재설정에 사용하는 이메일 인증 요청과 처리 상태를 저장한다. 인증번호 원문은 저장하지 않고 BCrypt 해시만 저장한다.

| 컬럼 | 타입 | 키 | Null | 기본값 | 설명 |
|---|---|---|---|---|---|
| `id` | BIGINT | PK, INDEX | X | AUTO_INCREMENT | 인증 요청 식별자 |
| `email` | VARCHAR(255) | INDEX | X | 없음 | 인증 대상 이메일 |
| `purpose` | VARCHAR(30) | INDEX | X | 없음 | 인증 목적 |
| `code_hash` | VARCHAR(100) |  | X | 없음 | 인증번호 BCrypt 해시 |
| `expires_at` | DATETIME(6) | INDEX | X | 없음 | 인증번호 만료 시각 |
| `attempt_count` | INT UNSIGNED |  | X | `0` | 실패한 확인 횟수 |
| `verified_at` | DATETIME(6) |  | O | NULL | 인증 완료 시각 |
| `consumed_at` | DATETIME(6) |  | O | NULL | 회원가입 등에 사용된 시각 |
| `created_at` | DATETIME(6) | INDEX | X | `CURRENT_TIMESTAMP(6)` | 생성 시각 |
| `updated_at` | DATETIME(6) |  | X | `CURRENT_TIMESTAMP(6)` | 수정 시각, 수정 시 자동 갱신 |

- CHECK: `purpose IN ('SIGNUP', 'PASSWORD_RESET')`
- CHECK: `attempt_count <= 5`
- INDEX: `idx_email_verifications_email_purpose_created` (`email`, `purpose`, `created_at DESC`, `id DESC`)
- INDEX: `idx_email_verifications_expires_at` (`expires_at`)
- 회원가입 전 요청도 저장하므로 `members` 외래 키를 두지 않는다.

## 관련 migration

- `V0__initial_schema.sql`
- `V3__add_member_name.sql`
- `V20260729_123306__add_member_status_constraint.sql`
- `V20260729_181617__add_member_birth_date.sql`
- `V20260803_091727__add_member_status_histories.sql`
- `V20260803_112954__backfill_withdrawn_member_status_histories.sql`
- `V20260811_174339__add_email_verifications.sql`
