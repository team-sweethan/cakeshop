-- add_order_in_production_status
-- 생성: 2026-08-11 14:57:23
--
-- 규칙
-- * 이 파일은 공유된 뒤 수정하지 않는다. 변경이 필요하면 새 migration 을 만든다.
-- * 로컬 샘플 데이터는 여기 넣지 않는다. db/seed/seed-local.sql 을 쓴다.
-- * 한 migration 은 하나의 배포 가능한 스키마 전환을 담는다. SQL 수만으로 나누거나 합치지 않는다.

-- 수제 주문 제작 시작 상태를 허용한다. 기존 READY_FOR_PICKUP 수제 주문은 이미 제작 완료된
-- 주문이므로 별도 보정 없이 유지한다.
ALTER TABLE `orders`
    DROP CONSTRAINT `chk_orders_status`,
    ADD CONSTRAINT `chk_orders_status`
        CHECK (`status` IN (
            'PENDING_PAYMENT',
            'UNDER_REVIEW',
            'IN_PRODUCTION',
            'READY_FOR_PICKUP',
            'PICKED_UP',
            'CANCELED',
            'REJECTED',
            'EXPIRED'
        ));
