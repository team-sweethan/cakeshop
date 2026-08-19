-- add_member_coupons_issued_at_index
-- 생성: 2026-08-19 13:46:00
--
-- 규칙
-- * 이 파일은 공유된 뒤 수정하지 않는다. 변경이 필요하면 새 migration 을 만든다.
-- * 로컬 샘플 데이터는 여기 넣지 않는다. db/seed/seed-local.sql 을 쓴다.
-- * 한 migration 은 하나의 배포 가능한 스키마 전환을 담는다. SQL 수만으로 나누거나 합치지 않는다.

-- member_coupons 테이블에 쿠폰 발급 알림 동기화용 복합 인덱스 (issued_at, id) 추가
CREATE INDEX idx_member_coupons_issued_at_id ON member_coupons (issued_at, id);
