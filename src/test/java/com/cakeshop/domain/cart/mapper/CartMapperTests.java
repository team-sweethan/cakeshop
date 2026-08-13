package com.cakeshop.domain.cart.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.cakeshop.domain.cart.entity.CartItem;
import com.cakeshop.domain.cart.entity.CartItemOption;
import com.cakeshop.global.config.MariaDbIntegrationTest;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.jdbc.core.JdbcTemplate;

@MybatisTest
@MariaDbIntegrationTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class CartMapperTests {

    @Autowired
    private CartMapper cartMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private long memberId;
    private long productId;

    @BeforeEach
    void setUp() {
        memberId = insertMember();
        productId = insertProduct();
    }

    @Test
    void insertAndFindItems_memberOwnershipIsApplied() {
        cartMapper.insertCartIfAbsent(memberId);
        long cartId = cartMapper.findCartIdByMemberId(memberId).orElseThrow();
        CartItem item = new CartItem();
        item.setCartId(cartId);
        item.setProductId(productId);
        item.setQuantity(2);

        assertThat(cartMapper.insertItem(item)).isEqualTo(1);

        assertThat(cartMapper.findItemsByMemberId(memberId))
                .extracting(CartItem::getId)
                .containsExactly(item.getId());
        assertThat(cartMapper.countItemsByMemberId(memberId)).isEqualTo(1);
        assertThat(cartMapper.findItemsByMemberId(memberId + 999)).isEmpty();
        assertThat(cartMapper.countItemsByMemberId(memberId + 999)).isZero();
    }

    @Test
    void insertOptionAndDeleteItem_removesOwnedChildren() {
        cartMapper.insertCartIfAbsent(memberId);
        long cartId = cartMapper.findCartIdByMemberId(memberId).orElseThrow();
        CartItem item = new CartItem();
        item.setCartId(cartId);
        item.setProductId(productId);
        item.setQuantity(1);
        cartMapper.insertItem(item);
        long optionId = insertOption(productId);
        CartItemOption option = new CartItemOption();
        option.setCartItemId(item.getId());
        option.setProductOptionId(optionId);
        option.setOptionName("초코");
        option.setAdditionalPrice(BigDecimal.valueOf(2000));
        cartMapper.insertItemOption(option);

        cartMapper.deleteOptionsByMemberIdAndItemIds(memberId, List.of(item.getId()));
        int deleted = cartMapper.deleteItemsByMemberIdAndItemIds(memberId, List.of(item.getId()));

        assertThat(deleted).isEqualTo(1);
        assertThat(cartMapper.findItemsByMemberId(memberId)).isEmpty();
    }

    private long insertMember() {
        jdbcTemplate.update(
                "INSERT INTO members (email, password, name, nickname, phone, role, status) "
                        + "VALUES (?, 'encoded', '회원', '회원', '010-0000-0000', 'USER', 'ACTIVE')",
                "cart-" + System.nanoTime() + "@test.local");
        return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    private long insertProduct() {
        jdbcTemplate.update("INSERT INTO categories (name, code, is_active) VALUES (?, ?, 1)",
                "테스트", "CART_" + System.nanoTime());
        long categoryId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        jdbcTemplate.update(
                "INSERT INTO products (category_id, name, base_price, product_type, status, stock_quantity) "
                        + "VALUES (?, '테스트 상품', 30000, 'GENERAL', 'ACTIVE', 10)",
                categoryId);
        return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    private long insertOption(long targetProductId) {
        jdbcTemplate.update(
                "INSERT INTO product_option_groups "
                        + "(product_id, name, required, selection_type, status) "
                        + "VALUES (?, '맛', 1, 'SINGLE', 'ACTIVE')",
                targetProductId);
        long groupId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        jdbcTemplate.update(
                "INSERT INTO product_options (option_group_id, name, additional_price, status) "
                        + "VALUES (?, '초코', 2000, 'ACTIVE')",
                groupId);
        return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }
}
