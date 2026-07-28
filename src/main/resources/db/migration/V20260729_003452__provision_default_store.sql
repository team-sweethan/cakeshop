-- provision_default_store
-- 생성: 2026-07-29 00:34:52
--
-- 규칙
-- * 이 파일은 머지된 뒤 절대 수정하지 않는다. 변경이 필요하면 새 migration 을 만든다.
-- * 로컬 샘플 데이터는 여기 넣지 않는다. db/seed/seed-local.sql 을 쓴다.
-- * 서로 의존하는 DDL 은 파일을 나누지 말고 이 파일에 함께 담는다.

-- StoreService.DEFAULT_STORE_ID가 참조하는 대표 매장은 모든 환경의 실행 필수 데이터다.
-- 이미 운영자가 설정한 값이 있으면 덮어쓰지 않고, 없는 행만 기본값으로 보충한다.
INSERT INTO `store`
    (`id`, `name`, `description`, `address`, `phone`,
    `pickup_place`, `pickup_start_time`, `pickup_end_time`, `pickup_interval_minutes`)
SELECT
    1, '케이크 공방', '수제 케이크 전문 매장입니다.', '서울특별시 강남구 테헤란로 1', '02-000-0000',
    '매장 1층 픽업 데스크', '10:00:00', '20:00:00', 30
WHERE NOT EXISTS (
    SELECT 1
      FROM `store`
     WHERE `id` = 1
);

-- StoreService.getStoreView()가 일주일 전체를 표시할 수 있도록 누락된 요일만 보충한다.
INSERT INTO `store_business_hour`
    (`store_id`, `day_of_week`, `open_time`, `close_time`, `is_closed`)
SELECT defaults.`store_id`,
       defaults.`day_of_week`,
       defaults.`open_time`,
       defaults.`close_time`,
       defaults.`is_closed`
  FROM (
        SELECT 1 AS `store_id`, 'MONDAY' AS `day_of_week`,
               CAST('10:00:00' AS TIME) AS `open_time`,
               CAST('20:00:00' AS TIME) AS `close_time`, 0 AS `is_closed`
        UNION ALL
        SELECT 1, 'TUESDAY', CAST('10:00:00' AS TIME), CAST('20:00:00' AS TIME), 0
        UNION ALL
        SELECT 1, 'WEDNESDAY', CAST('10:00:00' AS TIME), CAST('20:00:00' AS TIME), 0
        UNION ALL
        SELECT 1, 'THURSDAY', CAST('10:00:00' AS TIME), CAST('20:00:00' AS TIME), 0
        UNION ALL
        SELECT 1, 'FRIDAY', CAST('10:00:00' AS TIME), CAST('20:00:00' AS TIME), 0
        UNION ALL
        SELECT 1, 'SATURDAY', CAST('11:00:00' AS TIME), CAST('21:00:00' AS TIME), 0
        UNION ALL
        SELECT 1, 'SUNDAY', NULL, NULL, 1
       ) defaults
  LEFT JOIN `store_business_hour` existing
    ON existing.`store_id` = defaults.`store_id`
   AND existing.`day_of_week` = defaults.`day_of_week`
 WHERE existing.`id` IS NULL;
