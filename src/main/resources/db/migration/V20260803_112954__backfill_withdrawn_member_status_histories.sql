-- backfill_withdrawn_member_status_histories
-- 생성: 2026-08-03 11:29:54
--
-- 규칙
-- * 이 파일은 머지된 뒤 절대 수정하지 않는다. 변경이 필요하면 새 migration 을 만든다.
-- * 로컬 샘플 데이터는 여기 넣지 않는다. db/seed/seed-local.sql 을 쓴다.
-- * 서로 의존하는 DDL 은 파일을 나누지 말고 이 파일에 함께 담는다.
INSERT INTO member_status_histories (
    member_id,
    action,
    before_status,
    after_status,
    reason,
    processed_by,
    processed_at
)
SELECT member.id,
       'SUSPEND',
       'ACTIVE',
       'SUSPENDED',
       COALESCE(
           NULLIF(TRIM(member.suspended_reason), ''),
           '기존 이용정지 데이터'
       ),
       NULL,
       COALESCE(
           member.suspended_at,
           member.updated_at,
           member.created_at,
           CURRENT_TIMESTAMP(6)
       )
  FROM members member
 WHERE member.status = 'WITHDRAWN'
   AND (
       member.suspended_at IS NOT NULL
       OR NULLIF(TRIM(member.suspended_reason), '') IS NOT NULL
   )
   AND NOT EXISTS (
       SELECT 1
         FROM member_status_histories history
        WHERE history.member_id = member.id
          AND history.action = 'SUSPEND'
   );
