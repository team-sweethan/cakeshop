package com.cakeshop.domain.coupon.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.jdbc.core.JdbcTemplate;

import com.cakeshop.domain.coupon.dto.form.CouponSearchCondition;
import com.cakeshop.domain.coupon.dto.view.CouponView;
import com.cakeshop.domain.coupon.entity.DiscountType;
import com.cakeshop.global.config.MariaDbIntegrationTest;

/** 쿠폰 목록 정렬 SQL을 실제 MariaDB에서 검증한다. */
@MybatisTest
@MariaDbIntegrationTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class CouponMapperTests {

    @Autowired
    private CouponMapper couponMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void findCoupons_exhaustedCoupons_ordersByMostRecentIssuanceUpdate() {
        long adminId = insertAdmin();
        insertExhaustedCoupon(adminId, "older-exhausted", 30);
        insertExhaustedCoupon(adminId, "newer-exhausted", 10);

        List<CouponView> coupons = couponMapper.findCoupons(
                new CouponSearchCondition(),
                10,
                0
        );

        assertThat(coupons)
                .extracting(CouponView::name)
                .containsExactly("newer-exhausted", "older-exhausted");
    }

    @Test
    void findCoupons_filtersByDiscountTypeAndOverlappingPeriod() {
        long adminId = insertAdmin();
        LocalDateTime now = LocalDateTime.now();
        insertSearchCoupon(
                adminId,
                "matching-percentage",
                DiscountType.PERCENTAGE,
                now.minusDays(1),
                now.plusDays(1)
        );
        insertSearchCoupon(
                adminId,
                "fixed-amount",
                DiscountType.FIXED_AMOUNT,
                now.minusDays(1),
                now.plusDays(1)
        );
        insertSearchCoupon(
                adminId,
                "expired-percentage",
                DiscountType.PERCENTAGE,
                now.minusDays(4),
                now.minusDays(2)
        );

        CouponSearchCondition condition = new CouponSearchCondition();
        condition.setDiscountTypeFilter(DiscountType.PERCENTAGE);
        condition.setPeriodStart(now.minusHours(1));
        condition.setPeriodEnd(now.plusHours(1));

        assertThat(couponMapper.findCoupons(condition, 10, 0))
                .extracting(CouponView::name)
                .containsExactly("matching-percentage");
    }

    private long insertAdmin() {
        jdbcTemplate.update(
                "INSERT INTO members (email, password, name, nickname, phone, role, status) "
                        + "VALUES (?, 'encoded', 'coupon-admin', 'coupon-admin', "
                        + "'010-0000-0000', 'ADMIN', 'ACTIVE')",
                "coupon-sort-" + System.nanoTime() + "@test.local"
        );
        return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    private void insertExhaustedCoupon(long adminId, String name, int updatedMinutesAgo) {
        jdbcTemplate.update(
                "INSERT INTO coupons "
                        + "(name, discount_type, discount_value, minimum_order_amount, "
                        + "maximum_discount_amount, total_quantity, issued_quantity, starts_at, "
                        + "expires_at, status, target_type, created_by, updated_at) "
                        + "VALUES (?, 'FIXED_AMOUNT', 1000, 0, NULL, 1, 1, "
                        + "DATE_SUB(NOW(6), INTERVAL 1 DAY), DATE_ADD(NOW(6), INTERVAL 1 DAY), "
                        + "'ACTIVE', 'SPECIFIC_MEMBERS', ?, "
                        + "DATE_SUB(NOW(6), INTERVAL ? MINUTE))",
                name,
                adminId,
                updatedMinutesAgo
        );
    }

    private void insertSearchCoupon(
            long adminId,
            String name,
            DiscountType discountType,
            LocalDateTime startsAt,
            LocalDateTime expiresAt
    ) {
        jdbcTemplate.update(
                "INSERT INTO coupons "
                        + "(name, discount_type, discount_value, minimum_order_amount, "
                        + "maximum_discount_amount, total_quantity, issued_quantity, starts_at, "
                        + "expires_at, status, target_type, created_by) "
                        + "VALUES (?, ?, 10, 0, NULL, 2, 0, ?, ?, "
                        + "'ACTIVE', 'SPECIFIC_MEMBERS', ?)",
                name,
                discountType.name(),
                startsAt,
                expiresAt,
                adminId
        );
    }
}
