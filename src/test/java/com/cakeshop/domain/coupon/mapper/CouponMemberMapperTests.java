package com.cakeshop.domain.coupon.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.jdbc.core.JdbcTemplate;

import com.cakeshop.global.config.MariaDbIntegrationTest;

/** 회원가입 신규 회원 쿠폰 발급 전용 SQL의 대상·중복·수량 조건을 실제 MariaDB에서 검증한다. */
@MybatisTest
@MariaDbIntegrationTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class CouponMemberMapperTests {

    @Autowired
    private CouponMemberMapper couponMemberMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void newMemberCoupon_issuesOnlyOnceAndIncreasesIssuedQuantity() {
        long adminId = insertMember("ADMIN");
        long memberId = insertMember("USER");
        long couponId = insertNewMemberCoupon(adminId);

        assertThat(couponMemberMapper.findAvailableNewMemberCouponIds()).contains(couponId);
        assertThat(couponMemberMapper.insertNewMemberCouponIfAbsent(couponId, memberId)).isEqualTo(1);
        assertThat(couponMemberMapper.increaseNewMemberCouponIssuedQuantity(couponId)).isEqualTo(1);
        assertThat(couponMemberMapper.insertNewMemberCouponIfAbsent(couponId, memberId)).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT issued_quantity FROM coupons WHERE id = ?", Integer.class, couponId
        )).isEqualTo(1);
    }

    private long insertMember(String role) {
        jdbcTemplate.update(
                "INSERT INTO members (email, password, name, nickname, phone, role, status) "
                        + "VALUES (?, 'encoded', 'coupon-member', 'coupon-member', "
                        + "'010-0000-0000', ?, 'ACTIVE')",
                "coupon-member-" + System.nanoTime() + "@test.local",
                role
        );
        return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    private long insertNewMemberCoupon(long adminId) {
        jdbcTemplate.update(
                "INSERT INTO coupons "
                        + "(name, discount_type, discount_value, minimum_order_amount, "
                        + "maximum_discount_amount, total_quantity, issued_quantity, starts_at, "
                        + "expires_at, status, target_type, created_by) "
                        + "VALUES ('new-member', 'FIXED_AMOUNT', 1000, 0, NULL, NULL, 0, "
                        + "DATE_SUB(NOW(6), INTERVAL 1 DAY), DATE_ADD(NOW(6), INTERVAL 1 DAY), "
                        + "'ACTIVE', 'NEW_MEMBERS', ?)",
                adminId
        );
        return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }
}
