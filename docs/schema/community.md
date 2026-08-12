# 커뮤니티 스키마

- 담당: 현규
- 테이블: `post_categories`, `posts`, `comments`, `post_likes`, `post_images`, `post_reports`, `post_views`,
  `daily_popular_posts`, `popular_post_batch_runs`, `community_notices`
- 정본: Flyway migration 적용 결과

## `post_categories`

게시글 카테고리를 저장한다.

| 컬럼 | 타입 | 키 | Null | 기본값 | 의미 |
|---|---|---|---|---|---|
| `id` | BIGINT | PK | X | AUTO_INCREMENT | 카테고리 식별자 |
| `code` | VARCHAR(50) | UK | X | 없음 | 카테고리 코드 |
| `name` | VARCHAR(100) |  | X | 없음 | 카테고리명 |
| `is_active` | TINYINT(1) |  | X | `1` | 활성 여부 |
| `sort_order` | INT |  | X | `0` | 노출 순서 |

- UK: `uk_post_categories_code` (`code`)
- 기준 데이터: `QNA`, `REVIEW`, `FREE` 코드는 migration에서 없는 경우 생성한다.

## `posts`

게시글 본문, 상태와 조회·좋아요 집계값을 저장한다.

| 컬럼 | 타입 | 키 | Null | 기본값 | 의미 |
|---|---|---|---|---|---|
| `id` | BIGINT | PK, INDEX | X | AUTO_INCREMENT | 게시글 식별자 |
| `member_id` | BIGINT | FK | X | 없음 | 작성 회원 식별자 |
| `category_id` | BIGINT | FK | X | 없음 | 게시글 카테고리 식별자 |
| `title` | VARCHAR(200) |  | X | 없음 | 제목 |
| `content` | TEXT |  | X | 없음 | 본문 |
| `view_count` | BIGINT | INDEX | X | `0` | 조회 수 캐시 |
| `like_count` | BIGINT |  | X | `0` | 좋아요 수 캐시 |
| `status` | VARCHAR(30) | INDEX | X | `'PUBLISHED'` | 게시글 상태 |
| `blocked_at` | DATETIME(6) |  | O | NULL | 차단 시각 |
| `blocked_reason` | VARCHAR(500) |  | O | NULL | 차단 사유 |
| `blocked_by` | BIGINT | FK | O | NULL | 차단 처리 회원 식별자 |
| `created_at` | DATETIME(6) |  | X | `CURRENT_TIMESTAMP(6)` | 생성 시각 |
| `updated_at` | DATETIME(6) |  | X | `CURRENT_TIMESTAMP(6)` | 수정 시각, 수정 시 자동 갱신 |

- FK: `member_id`, `blocked_by` → 각각 `members.id`; `category_id` → `post_categories.id`
- CHECK: `chk_posts_status` — `status IN ('PUBLISHED', 'DELETED', 'BLOCKED')`
- INDEX: `ix_posts_status_view_count` (`status`, `view_count`, `id`)

## `comments`

게시글의 댓글과 대댓글을 저장한다.

| 컬럼 | 타입 | 키 | Null | 기본값 | 의미 |
|---|---|---|---|---|---|
| `id` | BIGINT | PK | X | AUTO_INCREMENT | 댓글 식별자 |
| `post_id` | BIGINT | FK, INDEX | X | 없음 | 게시글 식별자 |
| `member_id` | BIGINT | FK | X | 없음 | 작성 회원 식별자 |
| `parent_comment_id` | BIGINT | FK | O | NULL | 부모 댓글 식별자 |
| `content` | TEXT |  | X | 없음 | 댓글 내용 |
| `status` | VARCHAR(30) |  | X | `'PUBLISHED'` | 댓글 상태 |
| `created_at` | DATETIME(6) | INDEX | X | `CURRENT_TIMESTAMP(6)` | 생성 시각 |
| `updated_at` | DATETIME(6) |  | X | `CURRENT_TIMESTAMP(6)` | 수정 시각, 수정 시 자동 갱신 |

- FK: `post_id` → `posts.id`, `member_id` → `members.id`, `parent_comment_id` → `comments.id`
- CHECK: `chk_comments_status` — `status IN ('PUBLISHED', 'DELETED')`
- INDEX: `ix_comments_created` (`created_at`, `post_id`)

## `post_likes`

회원의 게시글 좋아요를 저장한다.

| 컬럼 | 타입 | 키 | Null | 기본값 | 의미 |
|---|---|---|---|---|---|
| `id` | BIGINT | PK | X | AUTO_INCREMENT | 좋아요 식별자 |
| `post_id` | BIGINT | FK, UK, INDEX | X | 없음 | 게시글 식별자 |
| `member_id` | BIGINT | FK, UK | X | 없음 | 회원 식별자 |
| `created_at` | DATETIME(6) | INDEX | X | `CURRENT_TIMESTAMP(6)` | 생성 시각 |

- UK: `uk_post_likes_post_member` (`post_id`, `member_id`)
- FK: `post_id` → `posts.id`, `member_id` → `members.id`
- INDEX: `ix_post_likes_created` (`created_at`, `post_id`)

## `post_images`

게시글 첨부 이미지를 저장한다.

| 컬럼 | 타입 | 키 | Null | 기본값 | 의미 |
|---|---|---|---|---|---|
| `id` | BIGINT | PK | X | AUTO_INCREMENT | 이미지 식별자 |
| `post_id` | BIGINT | FK | X | 없음 | 게시글 식별자 |
| `image_url` | VARCHAR(500) |  | X | 없음 | 이미지 URL |
| `sort_order` | INT |  | X | `0` | 노출 순서 |

- FK: `fk_post_images_post` — `post_id` → `posts.id`

## `post_reports`

회원의 게시글 신고와 처리 상태를 저장한다.

| 컬럼 | 타입 | 키 | Null | 기본값 | 의미 |
|---|---|---|---|---|---|
| `id` | BIGINT | PK | X | AUTO_INCREMENT | 신고 식별자 |
| `post_id` | BIGINT | FK, UK | X | 없음 | 신고 게시글 식별자 |
| `reporter_id` | BIGINT | FK, UK | X | 없음 | 신고 회원 식별자 |
| `reason` | VARCHAR(500) |  | X | 없음 | 신고 사유 |
| `status` | VARCHAR(30) |  | X | `'PENDING'` | 신고 처리 상태 |
| `created_at` | DATETIME(6) |  | X | `CURRENT_TIMESTAMP(6)` | 생성 시각 |

- UK: `uk_post_reports_post_reporter` (`post_id`, `reporter_id`)
- FK: `post_id` → `posts.id`, `reporter_id` → `members.id`
- CHECK: `chk_post_reports_status` — `status IN ('PENDING', 'RESOLVED', 'REJECTED')`

## `post_views`

게시글 조회 주체와 조회 시각을 저장한다. 현재 중복 조회 판단 창은 애플리케이션의 최근 10분 조회 조건을 사용한다.

| 컬럼 | 타입 | 키 | Null | 기본값 | 의미 |
|---|---|---|---|---|---|
| `id` | BIGINT | PK | X | AUTO_INCREMENT | 조회 이력 식별자 |
| `post_id` | BIGINT | FK, INDEX | X | 없음 | 게시글 식별자 |
| `viewer_key` | VARCHAR(100) | INDEX | X | 없음 | 회원 또는 세션 기반 조회자 키 |
| `viewed_on` | DATE |  | O | NULL | 이전 날짜 창 호환용 컬럼 |
| `created_at` | DATETIME(6) | INDEX | X | `CURRENT_TIMESTAMP(6)` | 조회 시각 |

- FK: `fk_post_views_post` — `post_id` → `posts.id`
- INDEX: `ix_post_views_post_viewer_created` (`post_id`, `viewer_key`, `created_at`)
- INDEX: `ix_post_views_created` (`created_at`, `post_id`)
- 현재 `post_id`, `viewer_key`, `viewed_on` 조합의 UK는 후속 migration에서 제거되었다.

## `daily_popular_posts`

날짜별 인기 게시글 순위와 산정 시점의 집계값을 저장한다.

| 컬럼 | 타입 | 키 | Null | 기본값 | 의미 |
|---|---|---|---|---|---|
| `ranking_date` | DATE | PK, UK | X | 없음 | 순위 기준일 |
| `ranking` | INT | PK | X | 없음 | 순위 |
| `post_id` | BIGINT | FK, UK | X | 없음 | 게시글 식별자 |
| `popularity_score` | BIGINT |  | X | 없음 | 인기 점수 |
| `view_count` | BIGINT |  | X | 없음 | 산정 시 조회 수 |
| `like_count` | BIGINT |  | X | 없음 | 산정 시 좋아요 수 |
| `comment_count` | BIGINT |  | X | 없음 | 산정 시 댓글 작성 회원 수 |
| `created_at` | DATETIME(6) |  | X | `CURRENT_TIMESTAMP(6)` | 생성 시각 |

- PK: (`ranking_date`, `ranking`)
- UK: `uk_daily_popular_post` (`ranking_date`, `post_id`)
- FK: `fk_daily_popular_posts_post` — `post_id` → `posts.id`

## `popular_post_batch_runs`

날짜별 인기글 배치 실행 결과를 저장한다.

| 컬럼 | 타입 | 키 | Null | 기본값 | 의미 |
|---|---|---|---|---|---|
| `ranking_date` | DATE | PK | X | 없음 | 실행 대상 기준일 |
| `post_count` | INT |  | X | 없음 | 산정된 게시글 수 |
| `executed_at` | DATETIME(6) |  | X | `CURRENT_TIMESTAMP(6)` | 실행 시각 |

## `community_notices`

관리자 공지사항을 저장한다. **게시글과 별도 표다** — 댓글·좋아요·신고·조회수가 구조적으로
불가능해야 하고, 인기글 집계에서도 빠져야 하기 때문이다(`docs/community/specs/community-notice.md` E4).

| 컬럼 | 타입 | 키 | Null | 기본값 | 의미 |
|---|---|---|---|---|---|
| `id` | BIGINT | PK | X | AUTO_INCREMENT | 공지 식별자 |
| `title` | VARCHAR(200) |  | X | 없음 | 제목 (화면 허용은 100자) |
| `content` | TEXT |  | X | 없음 | 본문. 순수 텍스트 |
| `status` | VARCHAR(20) |  | X | `'PUBLISHED'` | 공지 상태 |
| `starts_at` | DATETIME(6) |  | O | NULL | 노출 시작 시각. NULL이면 즉시 노출 |
| `ends_at` | DATETIME(6) |  | O | NULL | 노출 종료 시각. NULL이면 무기한 |
| `created_by` | BIGINT | FK | X | 없음 | 등록한 관리자. 감사용이며 화면에 쓰지 않는다 |
| `created_at` | DATETIME(6) |  | X | `CURRENT_TIMESTAMP(6)` | 생성 시각 |
| `updated_at` | DATETIME(6) |  | X | `CURRENT_TIMESTAMP(6)` | 수정 시각 (`ON UPDATE`) |

- FK: `fk_community_notices_member` — `created_by` → `members.id`
- CHECK: `chk_community_notices_status` — `status`는 `PUBLISHED`, `DELETED` 둘 중 하나다.
- CHECK: `chk_community_notices_period` — 시작·종료가 **둘 다 있을 때만** `starts_at < ends_at`이다.
  한쪽이 NULL이면 기간이 열려 있다는 뜻이라 비교 대상이 없다.
- 인덱스를 두지 않는다. 공지는 수십 건 규모이고 고객 정렬 키가 함수식이라 일반 인덱스로는 못 탄다.

## 관련 migration

- `V0__initial_schema.sql`
- `V20260802_113219__add_post_status_constraint.sql`
- `V20260802_113229__provision_post_categories.sql`
- `V20260803_235726__add_post_views.sql`
- `V20260804_074043__add_post_report_status_constraint.sql`
- `V20260804_102934__switch_post_view_window_to_10_minutes.sql`
- `V20260804_130038__add_post_view_count_sort_index.sql`
- `V20260805_073107__add_daily_popular_posts.sql`
- `V20260812_065639__add_community_notices.sql`
