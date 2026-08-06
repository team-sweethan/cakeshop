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

-- CHECK 를 걸기 전에 옛 어휘로 남은 행을 먼저 옮긴다.
-- 지금 reviews 는 비어 있고 INSERT 경로도 없지만, 한 행이라도 'VISIBLE' 로 남아 있으면
-- 아래 제약 추가가 배포 도중 실패한다. 한 줄로 막을 수 있는 것을 환경 상태에 맡기지 않는다.
-- 선례: V20260730_123931__apply_product_preparation_policy.sql (보정 UPDATE 후 CHECK)
UPDATE `reviews`
SET `status` = 'PUBLISHED'
WHERE `status` = 'VISIBLE';

-- 평점 4종은 1~5 정수다(SPEC.md 2.2).
--
-- 컬럼이 TINYINT UNSIGNED 라 DB 만으로는 0 과 255 가 들어간다. 화면 검증은 폼을 거친
-- 요청만 막으므로 API 를 직접 부르면 그대로 통과한다. 평점은 상품 정렬의 근거가 되는
-- 값이라(products.average_rating) 범위 밖 값 하나가 그 상품의 평균을 통째로 무너뜨린다.
--
-- 제약을 4개로 나눈 것은 위반했을 때 어느 평점이 잘못됐는지 제약 이름으로 드러나게
-- 하려는 것이다. 하나로 묶으면 chk_reviews_ratings 만 나와 넷 중 무엇인지 다시 찾아야 한다.

-- 상태와 평점을 ALTER 한 문장에 함께 담는다. 문장을 나누면 DDL 이 문장 단위로 암시적
-- 커밋되어, 뒤쪽 평점 제약이 범위 밖 행 때문에 실패해도 앞쪽 status 변경과
-- chk_reviews_status 는 이미 남는다. 그 뒤에 데이터를 고쳐 재실행하면 이번에는
-- chk_reviews_status 중복으로 실패해서 배포를 정상적으로 재시도할 수 없다.
-- 한 문장이면 어느 절이 실패하든 전부 되돌아가므로 데이터만 고치고 다시 실행하면 된다.
--
-- 복구 절차: 범위 밖 평점을 고치거나 지운 뒤 `flyway repair` 로 실패 기록을 지우고
-- 다시 마이그레이션한다. 이 파일은 절이 전부 적용되거나 전부 적용되지 않거나 둘 뿐이라
-- 중간 상태를 손으로 되돌릴 일이 없다. AGENTS.md 의 "기존 데이터 영향과 복구 방법"이다.
-- 회귀 방지: ReviewMigrationRetryTests.
--
-- 평점에는 status 와 달리 사전 보정 UPDATE 를 두지 않는다. 범위 밖 평점은 무엇으로
-- 고쳐야 옳은지 알 수 없어 임의로 정하면 사용자가 매긴 값을 조용히 바꾸게 된다.
-- 그래서 고치지 않고 실패시킨다. 근거는 SPEC.md 2.2.
ALTER TABLE `reviews`
    MODIFY COLUMN `status` VARCHAR(30) NOT NULL DEFAULT 'PUBLISHED',
    ADD CONSTRAINT `chk_reviews_status`
        CHECK (`status` IN ('PUBLISHED', 'DELETED', 'BLOCKED')),
    ADD CONSTRAINT `chk_reviews_overall_rating` CHECK (`overall_rating` BETWEEN 1 AND 5),
    ADD CONSTRAINT `chk_reviews_taste_rating`   CHECK (`taste_rating`   BETWEEN 1 AND 5),
    ADD CONSTRAINT `chk_reviews_design_rating`  CHECK (`design_rating`  BETWEEN 1 AND 5),
    ADD CONSTRAINT `chk_reviews_service_rating` CHECK (`service_rating` BETWEEN 1 AND 5);
