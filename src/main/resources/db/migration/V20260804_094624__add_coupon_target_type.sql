-- add_coupon_target_type
-- 생성: 2026-08-04 09:46:24
--
-- 규칙
-- * 이 파일은 머지된 뒤 절대 수정하지 않는다. 변경이 필요하면 새 migration 을 만든다.
-- * 로컬 샘플 데이터는 여기 넣지 않는다. db/seed/seed-local.sql 을 쓴다.
-- * 서로 의존하는 DDL 은 파일을 나누지 말고 이 파일에 함께 담는다.

-- 쿠폰별 발급 대상 정책을 구분하기 위해 target_type 컬럼을 추가한다.
-- 기존 데이터 호환을 위해 기본값을 SPECIFIC_MEMBERS로 설정한다.
ALTER TABLE coupons
    MODIFY COLUMN total_quantity INT UNSIGNED NULL DEFAULT NULL,

    ADD COLUMN target_type VARCHAR(30) NOT NULL
        DEFAULT 'SPECIFIC_MEMBERS'
    COMMENT '쿠폰 발급 대상 유형: ALL_MEMBERS, NEW_MEMBERS, FIRST_ORDER, BIRTHDAY, SPECIFIC_MEMBERS'
        AFTER status,

    ADD CONSTRAINT chk_coupons_target_type
        CHECK (
            target_type IN (
                'ALL_MEMBERS',
                'NEW_MEMBERS',
                'FIRST_ORDER',
                'BIRTHDAY',
                'SPECIFIC_MEMBERS'
            )
        ),

    ADD CONSTRAINT chk_coupons_quantity
        CHECK (
            total_quantity IS NULL
            OR issued_quantity <= total_quantity
        ),

    ADD CONSTRAINT chk_coupons_target_quantity
        CHECK (
            (target_type = 'SPECIFIC_MEMBERS' AND total_quantity IS NOT NULL)
            OR (target_type <> 'SPECIFIC_MEMBERS' AND total_quantity IS NULL)
        );
