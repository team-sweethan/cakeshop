-- provision_post_categories
-- 생성: 2026-08-02 11:32:29
--
-- 규칙
-- * 이 파일은 머지된 뒤 절대 수정하지 않는다. 변경이 필요하면 새 migration 을 만든다.
-- * 로컬 샘플 데이터는 여기 넣지 않는다. db/seed/seed-local.sql 을 쓴다.
-- * 서로 의존하는 DDL 은 파일을 나누지 말고 이 파일에 함께 담는다.

-- posts.category_id는 NOT NULL FK다. 카테고리가 하나도 없으면 게시글을 작성할 수 없으므로
-- 이 데이터는 로컬 샘플이 아니라 모든 환경의 실행 필수 참조 데이터다(docs/community/DOMAIN.md 6.8).
-- 선례: V20260729_003452__provision_default_store.sql
--
-- 운영자가 이미 이름이나 노출 여부를 바꿨을 수 있으므로 덮어쓰지 않고 없는 code만 보충한다.
INSERT INTO `post_categories` (`code`, `name`, `is_active`, `sort_order`)
SELECT defaults.`code`,
       defaults.`name`,
       defaults.`is_active`,
       defaults.`sort_order`
  FROM (
        SELECT 'QNA'    AS `code`, '질문' AS `name`, 1 AS `is_active`, 1 AS `sort_order`
        UNION ALL
        SELECT 'REVIEW', '후기', 1, 2
        UNION ALL
        SELECT 'FREE',   '자유', 1, 3
       ) defaults
  LEFT JOIN `post_categories` existing
    ON existing.`code` = defaults.`code`
 WHERE existing.`id` IS NULL;
