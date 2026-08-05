-- add_review_status_and_rating_constraints
-- 생성: 2026-08-06 07:51:14
--
-- 규칙
-- * 이 파일은 머지된 뒤 절대 수정하지 않는다. 변경이 필요하면 새 migration 을 만든다.
-- * 로컬 샘플 데이터는 여기 넣지 않는다. db/seed/seed-local.sql 을 쓴다.
-- * 서로 의존하는 DDL 은 파일을 나누지 말고 이 파일에 함께 담는다.

-- 후기 노출 여부는 reviews.status 하나로만 판단한다(docs/review/SPEC.md 2.1).
--
-- V0의 기본값 'VISIBLE'은 코드베이스 어디에도 없는 어휘다. 팀이 실제로 쓰는 상태 어휘는
-- ACTIVE/INACTIVE(관리자가 노출을 켜고 끈다)와 PUBLISHED/DELETED/BLOCKED(작성자가 쓰고
-- 관리자가 차단한다) 둘인데, 후기는 고객이 쓰고 관리자가 숨기는 구조라 PostStatus와 같다.
--
-- 지금 바꾸는 이유는 reviews 에 행이 한 건도 없어 교체 비용이 가장 싼 시점이기 때문이다.
-- 이후 모든 쿼리가 이 값을 전제하게 되면 데이터까지 함께 옮겨야 한다.
-- 선례: V20260802_113219__add_post_status_constraint.sql
ALTER TABLE `reviews`
    MODIFY COLUMN `status` VARCHAR(30) NOT NULL DEFAULT 'PUBLISHED',
    ADD CONSTRAINT `chk_reviews_status`
        CHECK (`status` IN ('PUBLISHED', 'DELETED', 'BLOCKED'));

-- 평점 4종은 1~5 정수다(SPEC.md 2.2).
--
-- 컬럼이 TINYINT UNSIGNED 라 DB 만으로는 0 과 255 가 들어간다. 화면 검증은 폼을 거친
-- 요청만 막으므로 API 를 직접 부르면 그대로 통과한다. 평점은 상품 정렬의 근거가 되는
-- 값이라(products.average_rating) 범위 밖 값 하나가 그 상품의 평균을 통째로 무너뜨린다.
--
-- 제약을 4개로 나눈 것은 위반했을 때 어느 평점이 잘못됐는지 제약 이름으로 드러나게
-- 하려는 것이다. 하나로 묶으면 chk_reviews_ratings 만 나와 넷 중 무엇인지 다시 찾아야 한다.
ALTER TABLE `reviews`
    ADD CONSTRAINT `chk_reviews_overall_rating` CHECK (`overall_rating` BETWEEN 1 AND 5),
    ADD CONSTRAINT `chk_reviews_taste_rating`   CHECK (`taste_rating`   BETWEEN 1 AND 5),
    ADD CONSTRAINT `chk_reviews_design_rating`  CHECK (`design_rating`  BETWEEN 1 AND 5),
    ADD CONSTRAINT `chk_reviews_service_rating` CHECK (`service_rating` BETWEEN 1 AND 5);
