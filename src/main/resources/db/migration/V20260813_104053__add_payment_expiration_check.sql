-- add_payment_expiration_check
-- 생성: 2026-08-13 10:40:53
--
-- 규칙
-- * 이 파일은 공유된 뒤 수정하지 않는다. 변경이 필요하면 새 migration 을 만든다.
-- * 로컬 샘플 데이터는 여기 넣지 않는다. db/seed/seed-local.sql 을 쓴다.
-- * 한 migration 은 하나의 배포 가능한 스키마 전환을 담는다. SQL 수만으로 나누거나 합치지 않는다.

-- NULL이면 미확인, 시각이 있으면 관리자가 결제 만료를 확인한 상태다.
-- 기존 만료 결제는 미확인 상태(NULL)로 남겨 운영자가 순차적으로 확인한다.
ALTER TABLE payments
    ADD COLUMN expiration_checked_at DATETIME(6) NULL AFTER canceled_at;
