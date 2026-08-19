-- 쿠폰 만료 임박 알림 동기화 조회 성능 최적화를 위한 인덱스 추가
CREATE INDEX idx_coupons_status_starts_expires ON coupons (status, starts_at, expires_at);
CREATE INDEX idx_member_coupons_status_member_coupon ON member_coupons (status, member_id, coupon_id);
CREATE INDEX idx_notifications_receiver_coupon_event ON notifications (receiver_id, user_coupon_id, event_key);
