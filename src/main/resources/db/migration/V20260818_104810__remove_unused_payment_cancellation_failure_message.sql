-- remove_unused_payment_cancellation_failure_message
-- 생성: 2026-08-18 10:48:10
--
-- 규칙
-- * 이 파일은 공유된 뒤 수정하지 않는다. 변경이 필요하면 새 migration 을 만든다.
-- * 로컬 샘플 데이터는 여기 넣지 않는다. db/seed/seed-local.sql 을 쓴다.
-- * 한 migration 은 하나의 배포 가능한 스키마 전환을 담는다. SQL 수만으로 나누거나 합치지 않는다.

-- payments 컬럼 제거와 분리해 payment_cancellations의 미사용 실패 메시지를 제거한다.
-- 앞 migration이 성공한 뒤 이 DDL이 실패해도, Flyway 이력상 재시도 대상은 이 파일만 남는다.
ALTER TABLE payment_cancellations
    DROP COLUMN failure_message;
