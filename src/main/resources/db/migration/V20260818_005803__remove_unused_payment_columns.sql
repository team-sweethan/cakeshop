-- remove_unused_payment_columns
-- 생성: 2026-08-18 00:58:03
--
-- 규칙
-- * 이 파일은 공유된 뒤 수정하지 않는다. 변경이 필요하면 새 migration 을 만든다.
-- * 로컬 샘플 데이터는 여기 넣지 않는다. db/seed/seed-local.sql 을 쓴다.
-- * 한 migration 은 하나의 배포 가능한 스키마 전환을 담는다. SQL 수만으로 나누거나 합치지 않는다.

-- 업무 상태 판단·조회에서 사용하지 않는 결제사 상태 및 실패 메시지를 제거한다.
-- 실패 원인 판별에 사용하는 failure_code와 모든 상태·멱등성 컬럼은 유지한다.
ALTER TABLE payments
    DROP COLUMN provider_status,
    DROP COLUMN failure_message;

ALTER TABLE payment_cancellations
    DROP COLUMN failure_message;
