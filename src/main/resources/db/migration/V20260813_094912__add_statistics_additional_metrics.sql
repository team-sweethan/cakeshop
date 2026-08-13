-- add_statistics_additional_metrics
-- 생성: 2026-08-13 09:49:12
--
-- 규칙
-- * 이 파일은 공유된 뒤 수정하지 않는다. 변경이 필요하면 새 migration 을 만든다.
-- * 로컬 샘플 데이터는 여기 넣지 않는다. db/seed/seed-local.sql 을 쓴다.
-- * 한 migration 은 하나의 배포 가능한 스키마 전환을 담는다. SQL 수만으로 나누거나 합치지 않는다.

-- 기존 집계일은 수치 기본값 0과 완료 시각 NULL로 두어 실제 0과 미집계를 구분한다.
-- 전체 기간 REBUILD가 기타 지표를 채운 뒤 additional_metrics_aggregated_at을 기록한다.
ALTER TABLE `daily_statistics`
    ADD COLUMN `new_member_count`
        BIGINT NOT NULL DEFAULT 0
        COMMENT '신규 회원 수'
        AFTER `total_sales_amount`,
    ADD COLUMN `withdrawn_member_count`
        BIGINT NOT NULL DEFAULT 0
        COMMENT '탈퇴 회원 수'
        AFTER `new_member_count`,
    ADD COLUMN `new_post_count`
        BIGINT NOT NULL DEFAULT 0
        COMMENT '새 게시글 수'
        AFTER `withdrawn_member_count`,
    ADD COLUMN `coupon_usage_count`
        BIGINT NOT NULL DEFAULT 0
        COMMENT '현재 유효한 쿠폰 사용 건수'
        AFTER `new_post_count`,
    ADD COLUMN `refund_amount`
        DECIMAL(18, 0) NOT NULL DEFAULT 0
        COMMENT '완료된 환불 금액'
        AFTER `coupon_usage_count`,
    ADD COLUMN `valid_payment_order_count`
        BIGINT NOT NULL DEFAULT 0
        COMMENT '평균 주문 금액 계산용 유효 결제 주문 수'
        AFTER `refund_amount`,
    ADD COLUMN `additional_metrics_aggregated_at`
        DATETIME(6) NULL
        COMMENT '활동 및 금액 지표 집계 완료 시각'
        AFTER `product_aggregated_at`,
    ADD CONSTRAINT `chk_daily_statistics_additional_counts`
        CHECK (
            `new_member_count` >= 0
            AND `withdrawn_member_count` >= 0
            AND `new_post_count` >= 0
            AND `coupon_usage_count` >= 0
            AND `valid_payment_order_count` >= 0
        ),
    ADD CONSTRAINT `chk_daily_statistics_refund_amount`
        CHECK (`refund_amount` >= 0);

-- 회원 가입·탈퇴 일별 범위 조회를 지원한다.
ALTER TABLE `members`
    ADD INDEX `idx_members_role_created_at` (`role`, `created_at`),
    ADD INDEX `idx_members_role_status_withdrawn_at`
        (`role`, `status`, `withdrawn_at`);

-- 게시글 상태와 무관한 작성 발생 건수를 날짜 범위로 조회한다.
ALTER TABLE `posts`
    ADD INDEX `idx_posts_created_at` (`created_at`);

-- 완료 환불의 일별 합계와 지연 완료된 환불의 변경 날짜 탐색을 지원한다.
ALTER TABLE `payment_cancellations`
    ADD INDEX `idx_payment_cancellations_status_canceled_at`
        (`status`, `canceled_at`),
    ADD INDEX `idx_payment_cancellations_updated_at_canceled_at`
        (`updated_at`, `canceled_at`);
