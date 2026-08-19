-- add_notification_retry_indexes
-- 생성: 2026-08-19 17:37:13
--
-- 규칙
-- * 이 파일은 공유된 뒤 수정하지 않는다. 변경이 필요하면 새 migration 을 만든다.
-- * 로컬 샘플 데이터는 여기 넣지 않는다. db/seed/seed-local.sql 을 쓴다.
-- * 한 migration 은 하나의 배포 가능한 스키마 전환을 담는다. SQL 수만으로 나누거나 합치지 않는다.

-- 알림 재시도 조회(notification_type, delivery_scope, id) 성능 최적화 복합 인덱스 추가
CREATE INDEX idx_notifications_type_scope_id ON notifications (notification_type, delivery_scope, id);
