-- member_coupons 테이블에 쿠폰 발급 알림 동기화용 복합 인덱스 (issued_at, id) 추가
CREATE INDEX idx_member_coupons_issued_at_id ON member_coupons (issued_at, id);
