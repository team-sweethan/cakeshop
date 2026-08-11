-- add_daily_product_statistics
-- 생성: 2026-08-11 16:33:16
--
-- 규칙
-- * 이 파일은 공유된 뒤 수정하지 않는다. 변경이 필요하면 새 migration 을 만든다.
-- * 로컬 샘플 데이터는 여기 넣지 않는다. db/seed/seed-local.sql 을 쓴다.
-- * 한 migration 은 하나의 배포 가능한 스키마 전환을 담는다. SQL 수만으로 나누거나 합치지 않는다.

-- 두 DDL은 상품별 집계 완료 상태와 결과 저장소를 함께 추가하는 하나의 전환이다.
-- MariaDB DDL의 부분 적용 후에도 재실행할 수 있도록 각 객체의 존재 여부를 확인한다.
ALTER TABLE `daily_statistics`
    ADD COLUMN IF NOT EXISTS `product_aggregated_at`
        DATETIME(6) NULL
        COMMENT '상품별 일별 집계 완료 시각'
        AFTER `aggregated_at`;

-- 날짜와 상품별 주문·판매·매출을 저장하는 재생성 가능한 파생 집계다.
-- product_id는 원본 식별값의 스냅샷이며 상품 원본의 수명 주기와 결합하는 FK는 두지 않는다.
CREATE TABLE IF NOT EXISTS `daily_product_statistics` (
    `statistics_date` DATE           NOT NULL,
    `product_id`      BIGINT         NOT NULL,
    `product_name`    VARCHAR(150)   NOT NULL,
    `order_count`     BIGINT         NOT NULL DEFAULT 0,
    `sales_quantity`  BIGINT         NOT NULL DEFAULT 0,
    `sales_amount`    DECIMAL(18, 0) NOT NULL DEFAULT 0,

    PRIMARY KEY (`statistics_date`, `product_id`),

    CONSTRAINT `chk_daily_product_statistics_counts`
        CHECK (
            `order_count` >= 0
            AND `sales_quantity` >= 0
        ),
    CONSTRAINT `chk_daily_product_statistics_sales_amount`
        CHECK (`sales_amount` >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
