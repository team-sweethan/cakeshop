-- add_post_status_constraint
-- 생성: 2026-08-02 11:32:19
--
-- 규칙
-- * 이 파일은 머지된 뒤 절대 수정하지 않는다. 변경이 필요하면 새 migration 을 만든다.
-- * 로컬 샘플 데이터는 여기 넣지 않는다. db/seed/seed-local.sql 을 쓴다.
-- * 서로 의존하는 DDL 은 파일을 나누지 말고 이 파일에 함께 담는다.

-- 게시글 노출 여부는 posts.status 하나로만 판단한다(docs/community/DOMAIN.md 4.1).
-- 판단 기준이 되는 컬럼이므로 허용값을 DB에서 강제해 오타나 미정의 상태가 저장되지 않게 한다.
-- 선례: V20260729_123306__add_member_status_constraint.sql
ALTER TABLE `posts`
    ADD CONSTRAINT `chk_posts_status`
        CHECK (`status` IN ('PUBLISHED', 'DELETED', 'BLOCKED'));

-- 댓글도 같은 이유로 상태값을 강제한다. 삭제된 댓글은 자리 표시로 남기므로
-- PUBLISHED/DELETED 두 값만 쓴다(DOMAIN.md 4.4).
ALTER TABLE `comments`
    ADD CONSTRAINT `chk_comments_status`
        CHECK (`status` IN ('PUBLISHED', 'DELETED'));
