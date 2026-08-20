-- 측정 데이터를 전부 지운다.
--
-- 행을 지우지 않고 DB 를 통째로 버린다. 이유가 둘이다.
--
-- 1. 행만 지우면 .ibd 파일이 안 줄어든다. 되돌리려면 OPTIMIZE TABLE 을 따로 돌려야 하는데
--    100만 행짜리 표에서는 그게 또 오래 걸린다. innodb_file_per_table 이 켜져 있으므로
--    DB 를 버리면 디스크가 실제로 OS 에 반환된다.
-- 2. 지울 것을 고르다 실수할 여지가 없다. 개발용 DB(cakeshop)와 이름이 다르므로
--    이 문장이 개발 데이터를 건드릴 방법이 없다.
--
-- 다시 만들려면:
--   CREATE DATABASE cakeshop_perf CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
--   앱을 cakeshop_perf 로 한 번 띄워 Flyway 가 스키마를 만들게 한 뒤
--   01 → 02 → 03 순서로 실행한다. 자세한 절차는 같은 폴더의 README.md 에 있다.

DROP DATABASE IF EXISTS cakeshop_perf;

SHOW DATABASES LIKE 'cakeshop%';
