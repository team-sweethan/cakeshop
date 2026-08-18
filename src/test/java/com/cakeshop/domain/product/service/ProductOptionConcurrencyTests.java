package com.cakeshop.domain.product.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.cakeshop.domain.product.dto.form.ProductOptionForm;
import com.cakeshop.domain.product.dto.form.ProductOptionGroupForm;
import com.cakeshop.domain.product.dto.form.ProductOptionMoveDirection;
import com.cakeshop.domain.product.entity.ProductOptionSelectionType;
import com.cakeshop.domain.product.entity.ProductOptionStatus;
import com.cakeshop.global.config.MariaDbIntegrationTest;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@MybatisTest
@Import(ProductOptionAdminService.class)
@MariaDbIntegrationTest
@AutoConfigureTestDatabase(
        replace = AutoConfigureTestDatabase.Replace.NONE
)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ProductOptionConcurrencyTests {

    private final ProductOptionAdminService productOptionAdminService;
    private final JdbcTemplate jdbcTemplate;

    private String suffix;
    private long categoryId;
    private long productId;
    private long optionGroupId;

    @Autowired
    ProductOptionConcurrencyTests(
            ProductOptionAdminService productOptionAdminService,
            JdbcTemplate jdbcTemplate
    ) {
        this.productOptionAdminService = productOptionAdminService;
        this.jdbcTemplate = jdbcTemplate;
    }

    @BeforeEach
    void setUp() {
        suffix = Long.toString(System.nanoTime());

        String categoryCode = "OPTION_RACE_" + suffix;
        jdbcTemplate.update(
                """
                INSERT INTO categories (
                    code, name, sort_order, is_active
                ) VALUES (?, ?, 999, 1)
                """,
                categoryCode,
                "옵션 동시성"
        );
        categoryId = jdbcTemplate.queryForObject(
                "SELECT id FROM categories WHERE code = ?",
                Long.class,
                categoryCode
        );

        String productName = "옵션 동시성 상품 " + suffix;
        jdbcTemplate.update(
                """
                INSERT INTO products (
                    category_id, name, description, base_price,
                    stock_quantity, product_type, preparation_days,
                    status
                ) VALUES (?, ?, '', 30000, 10, 'GENERAL', 0, 'INACTIVE')
                """,
                categoryId,
                productName
        );
        productId = jdbcTemplate.queryForObject(
                "SELECT id FROM products WHERE name = ?",
                Long.class,
                productName
        );

        String groupName = "크기 " + suffix;
        jdbcTemplate.update(
                """
                INSERT INTO product_option_groups (
                    product_id, name, required, selection_type,
                    status, sort_order
                ) VALUES (?, ?, 0, 'SINGLE', 'ACTIVE', 1)
                """,
                productId,
                groupName
        );
        optionGroupId = jdbcTemplate.queryForObject(
                """
                SELECT id
                FROM product_option_groups
                WHERE product_id = ? AND name = ?
                """,
                Long.class,
                productId,
                groupName
        );

        String optionName = "1호 " + suffix;
        jdbcTemplate.update(
                """
                INSERT INTO product_options (
                    option_group_id, name, additional_price,
                    status, sort_order
                ) VALUES (?, ?, 0, 'ACTIVE', 1)
                """,
                optionGroupId,
                optionName
        );
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update(
                """
                DELETE FROM product_options
                WHERE option_group_id IN (
                    SELECT id
                    FROM product_option_groups
                    WHERE product_id = ?
                )
                """,
                productId
        );
        jdbcTemplate.update(
                "DELETE FROM product_option_groups WHERE product_id = ?",
                productId
        );
        jdbcTemplate.update(
                "DELETE FROM products WHERE id = ?",
                productId
        );
        jdbcTemplate.update(
                "DELETE FROM categories WHERE id = ?",
                categoryId
        );
    }

    @Test
    void createOption_concurrentRequests_assignDistinctContinuousSortOrders()
            throws Exception {
        executeConcurrently(
                () -> productOptionAdminService.createOption(
                        productId,
                        optionGroupId,
                        optionForm("2호 " + suffix)
                ),
                () -> productOptionAdminService.createOption(
                        productId,
                        optionGroupId,
                        optionForm("3호 " + suffix)
                )
        );

        assertThat(optionSortOrders())
                .containsExactly(1, 2, 3);
    }

    @Test
    void createAndMoveOption_concurrentRequests_keepEveryOptionInContinuousOrder()
            throws Exception {
        String secondOptionName = "2호 " + suffix;
        insertOption(secondOptionName, 2);
        long secondOptionId = optionId(secondOptionName);

        String thirdOptionName = "3호 " + suffix;

        executeConcurrently(
                () -> productOptionAdminService.createOption(
                        productId,
                        optionGroupId,
                        optionForm(thirdOptionName)
                ),
                () -> {
                    productOptionAdminService.moveOption(
                            productId,
                            optionGroupId,
                            secondOptionId,
                            ProductOptionMoveDirection.UP
                    );
                    return null;
                }
        );

        assertThat(optionNamesInOrder())
                .containsExactly(
                        secondOptionName,
                        "1호 " + suffix,
                        thirdOptionName
                );
        assertThat(optionSortOrders())
                .containsExactly(1, 2, 3);
    }

    @Test
    void createAndMoveOptionGroup_concurrentRequests_keepGroupsInContinuousOrder()
            throws Exception {
        String secondGroupName = "맛 " + suffix;
        insertOptionGroup(secondGroupName, 2);
        long secondGroupId = optionGroupId(secondGroupName);

        String thirdGroupName = "장식 " + suffix;

        executeConcurrently(
                () -> productOptionAdminService.createOptionGroup(
                        productId,
                        optionGroupForm(thirdGroupName)
                ),
                () -> {
                    productOptionAdminService.moveOptionGroup(
                            productId,
                            secondGroupId,
                            ProductOptionMoveDirection.UP
                    );
                    return null;
                }
        );

        assertThat(optionGroupNamesInOrder())
                .containsExactly(
                        secondGroupName,
                        "크기 " + suffix,
                        thirdGroupName
                );
        assertThat(optionGroupSortOrders())
                .containsExactly(1, 2, 3);
    }

    private void executeConcurrently(
            Callable<?> first,
            Callable<?> second
    ) throws Exception {
        CyclicBarrier startLine = new CyclicBarrier(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<?> firstResult = executor.submit(() -> {
                startLine.await(10, TimeUnit.SECONDS);
                return first.call();
            });
            Future<?> secondResult = executor.submit(() -> {
                startLine.await(10, TimeUnit.SECONDS);
                return second.call();
            });

            firstResult.get(30, TimeUnit.SECONDS);
            secondResult.get(30, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
            executor.awaitTermination(30, TimeUnit.SECONDS);
        }
    }

    private ProductOptionForm optionForm(String name) {
        ProductOptionForm form = new ProductOptionForm();
        form.setName(name);
        form.setAdditionalPrice(BigDecimal.ZERO);
        form.setStatus(ProductOptionStatus.ACTIVE);
        return form;
    }

    private ProductOptionGroupForm optionGroupForm(String name) {
        ProductOptionGroupForm form = new ProductOptionGroupForm();
        form.setName(name);
        form.setRequired(false);
        form.setSelectionType(ProductOptionSelectionType.SINGLE);
        form.setStatus(ProductOptionStatus.ACTIVE);
        return form;
    }

    private void insertOption(String name, int sortOrder) {
        jdbcTemplate.update(
                """
                INSERT INTO product_options (
                    option_group_id, name, additional_price,
                    status, sort_order
                ) VALUES (?, ?, 0, 'ACTIVE', ?)
                """,
                optionGroupId,
                name,
                sortOrder
        );
    }

    private void insertOptionGroup(String name, int sortOrder) {
        jdbcTemplate.update(
                """
                INSERT INTO product_option_groups (
                    product_id, name, required, selection_type,
                    status, sort_order
                ) VALUES (?, ?, 0, 'SINGLE', 'ACTIVE', ?)
                """,
                productId,
                name,
                sortOrder
        );
    }

    private long optionId(String name) {
        return jdbcTemplate.queryForObject(
                """
                SELECT id
                FROM product_options
                WHERE option_group_id = ? AND name = ?
                """,
                Long.class,
                optionGroupId,
                name
        );
    }

    private long optionGroupId(String name) {
        return jdbcTemplate.queryForObject(
                """
                SELECT id
                FROM product_option_groups
                WHERE product_id = ? AND name = ?
                """,
                Long.class,
                productId,
                name
        );
    }

    private List<Integer> optionSortOrders() {
        return jdbcTemplate.queryForList(
                """
                SELECT sort_order
                FROM product_options
                WHERE option_group_id = ?
                ORDER BY sort_order, id
                """,
                Integer.class,
                optionGroupId
        );
    }

    private List<String> optionNamesInOrder() {
        return jdbcTemplate.queryForList(
                """
                SELECT name
                FROM product_options
                WHERE option_group_id = ?
                ORDER BY sort_order, id
                """,
                String.class,
                optionGroupId
        );
    }

    private List<Integer> optionGroupSortOrders() {
        return jdbcTemplate.queryForList(
                """
                SELECT sort_order
                FROM product_option_groups
                WHERE product_id = ?
                ORDER BY sort_order, id
                """,
                Integer.class,
                productId
        );
    }

    private List<String> optionGroupNamesInOrder() {
        return jdbcTemplate.queryForList(
                """
                SELECT name
                FROM product_option_groups
                WHERE product_id = ?
                ORDER BY sort_order, id
                """,
                String.class,
                productId
        );
    }
}
