-- add_post_report_status_constraint
-- 생성: 2026-08-04 07:40:43
--
-- 규칙
-- * 이 파일은 머지된 뒤 절대 수정하지 않는다. 변경이 필요하면 새 migration 을 만든다.
-- * 로컬 샘플 데이터는 여기 넣지 않는다. db/seed/seed-local.sql 을 쓴다.
-- * 서로 의존하는 DDL 은 파일을 나누지 말고 이 파일에 함께 담는다.

-- 신고 상태는 관리자가 그 글을 조치했는지를 뜻한다(docs/community/DOMAIN.md 6.6).
-- posts.status·comments.status 와 같은 이유로 허용값을 DB에서 강제한다 — 미정의 값이
-- 들어가면 "미처리 신고"를 세는 관리자 목록의 정렬이 조용히 틀리는데, 숫자만 달라질 뿐
-- 화면은 멀쩡히 그려져서 드러나지 않는다.
-- 선례: V20260802_113219__add_post_status_constraint.sql
ALTER TABLE `post_reports`
    ADD CONSTRAINT `chk_post_reports_status`
        CHECK (`status` IN ('PENDING', 'RESOLVED', 'REJECTED'));

