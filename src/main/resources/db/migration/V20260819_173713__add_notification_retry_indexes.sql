-- 알림 재시도 조회(notification_type, delivery_scope, created_at, id) 성능 최적화 복합 인덱스 추가
CREATE INDEX idx_notifications_type_scope_created_id ON notifications (notification_type, delivery_scope, created_at, id);
