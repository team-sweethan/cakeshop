package com.cakeshop.domain.product.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.jdbc.core.JdbcTemplate;

import com.cakeshop.global.config.MariaDbIntegrationTest;

/**
 * ******************************
 * 작성자 : HyunGyu-Cho
 * 담당자 : 시은
 * 작성일 : 2026-08-10
 * 기능 : 후기 집계 반영 SQL 검증
 * 설명 : products 갱신과 updated_at 보존을 MariaDB Testcontainers 로 고정한다.
 *        reviews 에 한 행도 넣지 않는다 - 여기에 reviews INSERT 가 생기면 경계가 무너진 것이다.
 * ******************************
 */
@MybatisTest
@MariaDbIntegrationTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ProductReviewMapperTests {

    @Autowired
    private ProductReviewMapper productReviewMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private long productId;

    @BeforeEach
    void setUp() {
        productId = insertProduct();
    }

    @Test
    void applyReviewAggregate_existingProduct_storesAverageAndCount() {
        int updatedRows = productReviewMapper.applyReviewAggregate(
                productId, new BigDecimal("4.50"), 2L);

        assertThat(updatedRows).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT average_rating FROM products WHERE id = ?", BigDecimal.class, productId))
                .isEqualByComparingTo("4.50");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT review_count FROM products WHERE id = ?", Long.class, productId))
                .isEqualTo(2);
    }

    @Test
    void applyReviewAggregate_updatedAt_isPreservedSoProductDoesNotLookEdited() {
        LocalDateTime before = jdbcTemplate.queryForObject(
                "SELECT updated_at FROM products WHERE id = ?", LocalDateTime.class, productId);

        productReviewMapper.applyReviewAggregate(productId, new BigDecimal("3.00"), 1L);

        assertThat(jdbcTemplate.queryForObject(
                "SELECT updated_at FROM products WHERE id = ?", LocalDateTime.class, productId))
                .isEqualTo(before);
    }

    @Test
    void applyReviewAggregate_missingProduct_updatesNothing() {
        assertThat(productReviewMapper.applyReviewAggregate(
                productId + 999_999L, new BigDecimal("5.00"), 1L))
                .isZero();
    }

    @Test
    void applyReviewAggregate_noPublishedReview_resetsToZero() {
        productReviewMapper.applyReviewAggregate(productId, new BigDecimal("4.50"), 2L);

        productReviewMapper.applyReviewAggregate(productId, BigDecimal.ZERO, 0L);

        assertThat(jdbcTemplate.queryForObject(
                "SELECT average_rating FROM products WHERE id = ?", BigDecimal.class, productId))
                .isEqualByComparingTo("0.00");
    }

    private long insertProduct() {
        String unique = String.valueOf(System.nanoTime());
        String categoryCode = "PRODUCT_REVIEW_" + unique;
        jdbcTemplate.update(
                "INSERT INTO categories (code, name, sort_order, is_active) VALUES (?, ?, 999, 1)",
                categoryCode,
                "집계 대상");
        long categoryId = jdbcTemplate.queryForObject(
                "SELECT id FROM categories WHERE code = ?", Long.class, categoryCode);

        String productName = "집계 대상 상품 " + unique;
        jdbcTemplate.update(
                """
                INSERT INTO products (
                    category_id, name, description, base_price, product_type,
                    preparation_days, stock_quantity, status
                ) VALUES (?, ?, '', 20000, 'GENERAL', 0, 5, 'ACTIVE')
                """,
                categoryId,
                productName);
        return jdbcTemplate.queryForObject(
                "SELECT id FROM products WHERE name = ?", Long.class, productName);
    }
}
