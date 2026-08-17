package com.cakeshop.domain.cart.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.cakeshop.domain.cart.dto.view.CartPaymentItemTarget;
import com.cakeshop.global.config.MariaDbIntegrationTest;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@MybatisTest
@MariaDbIntegrationTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class CartPaymentMapperTests {

    @Autowired
    private CartPaymentMapper cartPaymentMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private long memberId;
    private long categoryId;
    private long productId;
    private long cartId;
    private long cartItemId;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update(
                "INSERT INTO members (email, password, name, nickname, phone, role, status) "
                        + "VALUES (?, 'encoded', '회원', ?, '010-0000-0000', 'USER', 'ACTIVE')",
                "cart-payment-" + System.nanoTime() + "@test.local",
                "결제정리-" + System.nanoTime());
        memberId = lastInsertId();
        jdbcTemplate.update(
                "INSERT INTO categories (name, code, is_active) VALUES (?, ?, 1)",
                "결제 정리 테스트", "CART_PAYMENT_" + System.nanoTime());
        categoryId = lastInsertId();
        jdbcTemplate.update(
                "INSERT INTO products "
                        + "(category_id, name, base_price, product_type, status, stock_quantity) "
                        + "VALUES (?, '결제 정리 상품', 30000, 'GENERAL', 'ACTIVE', 10)",
                categoryId);
        productId = lastInsertId();
        jdbcTemplate.update("INSERT INTO carts (member_id) VALUES (?)", memberId);
        cartId = lastInsertId();
        jdbcTemplate.update(
                "INSERT INTO cart_items (cart_id, product_id, quantity) VALUES (?, ?, 1)",
                cartId, productId);
        cartItemId = lastInsertId();
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.update("DELETE FROM cart_items WHERE id = ?", cartItemId);
        jdbcTemplate.update("DELETE FROM carts WHERE id = ?", cartId);
        jdbcTemplate.update("DELETE FROM products WHERE id = ?", productId);
        jdbcTemplate.update("DELETE FROM categories WHERE id = ?", categoryId);
        jdbcTemplate.update("DELETE FROM members WHERE id = ?", memberId);
    }

    @Test
    void findItemIdsMatchingSnapshotQuantity_afterSnapshot_readsLatestCommittedQuantity() {
        TransactionTemplate cleanupTransaction = new TransactionTemplate(transactionManager);

        cleanupTransaction.executeWithoutResult(status -> {
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT quantity FROM cart_items WHERE id = ?",
                    Integer.class,
                    cartItemId)).isOne();

            TransactionTemplate userChangeTransaction = new TransactionTemplate(transactionManager);
            userChangeTransaction.setPropagationBehavior(
                    TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            userChangeTransaction.executeWithoutResult(innerStatus ->
                    jdbcTemplate.update(
                            "UPDATE cart_items SET quantity = 2 WHERE id = ?",
                            cartItemId));

            assertThat(cartPaymentMapper.findItemIdsMatchingSnapshotQuantity(
                    memberId,
                    List.of(new CartPaymentItemTarget(cartItemId, 1))))
                    .isEmpty();
            assertThat(cartPaymentMapper.findItemIdsMatchingSnapshotQuantity(
                    memberId,
                    List.of(new CartPaymentItemTarget(cartItemId, 2))))
                    .containsExactly(cartItemId);
        });
    }

    private long lastInsertId() {
        return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }
}
