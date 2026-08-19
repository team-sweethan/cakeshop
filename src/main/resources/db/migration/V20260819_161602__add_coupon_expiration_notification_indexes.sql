-- add_coupon_expiration_notification_indexes
-- 생성: 2026-08-19 16:16:02
--
-- 규칙
-- * 이 파일은 공유된 뒤 수정하지 않는다. 변경이 필요하면 새 migration 을 만든다.
-- * 로컬 샘플 데이터는 여기 넣지 않는다. db/seed/seed-local.sql 을 쓴다.
-- * 한 migration 은 하나의 배포 가능한 스키마 전환을 담는다. SQL 수만으로 나누거나 합치지 않는다.

-- 쿠폰 만료 임박 알림 동기화 조회 성능 최적화를 위한 인덱스 추가
CREATE INDEX idx_coupons_status_starts_expires ON coupons (status, starts_at, expires_at);
CREATE INDEX idx_member_coupons_status_member_coupon ON member_coupons (status, member_id, coupon_id);
