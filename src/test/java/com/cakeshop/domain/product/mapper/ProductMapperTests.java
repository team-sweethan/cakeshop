package com.cakeshop.domain.product.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import com.cakeshop.domain.product.customer.dto.form.ProductSearchCondition;
import com.cakeshop.domain.product.customer.dto.form.ProductSort;
import com.cakeshop.domain.product.customer.dto.form.StockFilter;
import com.cakeshop.domain.product.customer.dto.view.ProductListView;
import com.cakeshop.domain.product.entity.ProductType;

import com.cakeshop.global.config.MariaDbIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@MybatisTest
//@ActiveProfiles("local")
@MariaDbIntegrationTest
@AutoConfigureTestDatabase(
        replace = AutoConfigureTestDatabase.Replace.NONE
)
class ProductMapperTests {

    @Autowired
    private ProductMapper productMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String keyword;
    private long categoryId;
    private long optionProductId;

    @BeforeEach
    void setUp() {
        String suffix = Long.toString(System.nanoTime());
        String categoryCode = "PRODUCT_LIST_TEST_" + suffix;
        keyword = "[PLT-" + suffix + "]";

        jdbcTemplate.update(
                """
                INSERT INTO categories (
                    code,
                    name,
                    sort_order,
                    is_active
                )
                VALUES (?, ?, 999, 1)
                """,
                categoryCode,
                "상품 목록 테스트"
        );

        categoryId = jdbcTemplate.queryForObject(
                "SELECT id FROM categories WHERE code = ?",
                Long.class,
                categoryCode
        );

        insertProduct(
                "A 당일 재고 상품",
                10_000,
                10,
                ProductType.GENERAL,
                0,
                "ACTIVE",
                "4.50",
                5,
                LocalDateTime.of(2026, 1, 1, 10, 0)
        );
        insertProduct(
                "B 품절 상품",
                20_000,
                0,
                ProductType.GENERAL,
                2,
                "ACTIVE",
                "4.00",
                1,
                LocalDateTime.of(2026, 2, 1, 10, 0)
        );
        insertProduct(
                "C 인기 주문 제작",
                30_000,
                null,
                ProductType.CUSTOM,
                4,
                "ACTIVE",
                "4.90",
                10,
                LocalDateTime.of(2026, 3, 1, 10, 0)
        );
        insertProduct(
                "D 판매 중지 상품",
                40_000,
                5,
                ProductType.GENERAL,
                0,
                "INACTIVE",
                "5.00",
                100,
                LocalDateTime.of(2026, 4, 1, 10, 0)
        );
        insertProduct(
                "E 최대 가격 상품",
                100_000,
                1,
                ProductType.GENERAL,
                3,
                "ACTIVE",
                "3.00",
                0,
                LocalDateTime.of(2026, 5, 1, 10, 0)
        );
        insertProduct(
                "F 10만원 초과 주문 제작",
                120_000,
                null,
                ProductType.CUSTOM,
                6,
                "ACTIVE",
                "4.80",
                8,
                LocalDateTime.of(2026, 6, 1, 10, 0)
        );

        optionProductId = jdbcTemplate.queryForObject(
                "SELECT id FROM products WHERE name = ?",
                Long.class,
                keyword + " A 당일 재고 상품"
        );

        jdbcTemplate.update(
                """
                INSERT INTO product_option_groups (
                    product_id,
                    name,
                    required,
                    selection_type,
                    sort_order
                )
                VALUES (?, '케이크 크기', 1, 'SINGLE', 1)
                """,
                optionProductId
        );

        long optionGroupId = jdbcTemplate.queryForObject(
                """
                SELECT id
                FROM product_option_groups
                WHERE product_id = ?
                  AND name = '케이크 크기'
                """,
                Long.class,
                optionProductId
        );

        jdbcTemplate.update(
                """
                INSERT INTO product_options (
                    option_group_id,
                    name,
                    additional_price,
                    status,
                    sort_order
                )
                VALUES
                    (?, '1호', 0, 'ACTIVE', 1),
                    (?, '2호', 10000, 'ACTIVE', 2),
                    (?, '단종 크기', 20000, 'INACTIVE', 3)
                """,
                optionGroupId,
                optionGroupId,
                optionGroupId
        );
    }

    @Test
    void publicListExcludesInactiveAndAppliesPagination() {
        ProductSearchCondition condition =
                baseCondition();

        condition.setMaxPrice(BigDecimal.valueOf(200_000));
        condition.setSort(ProductSort.PRICE_ASC);

        assertThat(productMapper.countPublicProducts(condition))
                .isEqualTo(5);

        List<ProductListView> firstPage =
                productMapper.findPublicProducts(
                        condition,
                        2,
                        0
                );
        List<ProductListView> secondPage =
                productMapper.findPublicProducts(
                        condition,
                        2,
                        2
                );

        assertThat(firstPage)
                .extracting(ProductListView::name)
                .containsExactly(
                        keyword + " A 당일 재고 상품",
                        keyword + " B 품절 상품"
                );

        assertThat(secondPage)
                .extracting(ProductListView::name)
                .containsExactly(
                        keyword + " C 인기 주문 제작",
                        keyword + " E 최대 가격 상품"
                );
    }

    @Test
    void typeStockAndSameDayFiltersCanBeCombined() {
        ProductSearchCondition condition =
                baseCondition();

        condition.setMaxPrice(BigDecimal.valueOf(200_000));
        condition.setType(ProductType.GENERAL);
        condition.setStock(StockFilter.AVAILABLE);
        condition.setSameDay(true);

        List<ProductListView> products =
                productMapper.findPublicProducts(
                        condition,
                        10,
                        0
                );

        assertThat(products)
                .extracting(ProductListView::name)
                .containsExactly(
                        keyword + " A 당일 재고 상품"
                );
    }

    @Test
    void outOfStockAndPopularSortAreApplied() {
        ProductSearchCondition outOfStockCondition =
                baseCondition();

        outOfStockCondition.setMaxPrice(
                BigDecimal.valueOf(200_000)
        );
        outOfStockCondition.setStock(
                StockFilter.OUT_OF_STOCK
        );

        assertThat(productMapper.findPublicProducts(
                outOfStockCondition,
                10,
                0
        )).extracting(ProductListView::name)
                .containsExactly(
                        keyword + " B 품절 상품"
                );

        ProductSearchCondition popularCondition =
                baseCondition();

        popularCondition.setMaxPrice(
                BigDecimal.valueOf(200_000)
        );
        popularCondition.setSort(ProductSort.POPULAR);

        assertThat(productMapper.findPublicProducts(
                popularCondition,
                10,
                0
        )).extracting(ProductListView::name)
                .containsExactly(
                        keyword + " C 인기 주문 제작",
                        keyword + " F 10만원 초과 주문 제작",
                        keyword + " A 당일 재고 상품",
                        keyword + " B 품절 상품",
                        keyword + " E 최대 가격 상품"
                );
    }

    @Test
    void detailOptionsIncludeOnlyActiveOptionsInOrder() {
        assertThat(productMapper
                .findPublicOptionRowsByProductId(optionProductId))
                .extracting(row -> row.optionName())
                .containsExactly("1호", "2호");
    }

    private ProductSearchCondition baseCondition() {
        ProductSearchCondition condition =
                new ProductSearchCondition();

        condition.setKeyword(keyword);

        return condition;
    }

    private void insertProduct(
            String name,
            int price,
            Integer stockQuantity,
            ProductType productType,
            int preparationDays,
            String status,
            String averageRating,
            int reviewCount,
            LocalDateTime createdAt
    ) {
        jdbcTemplate.update(
                """
                INSERT INTO products (
                    category_id,
                    name,
                    description,
                    base_price,
                    stock_quantity,
                    product_type,
                    preparation_days,
                    cancellation_limit_days,
                    status,
                    average_rating,
                    review_count,
                    created_at
                )
                VALUES (?, ?, '', ?, ?, ?, ?, 0, ?, ?, ?, ?)
                """,
                categoryId,
                keyword + " " + name,
                price,
                stockQuantity,
                productType.name(),
                preparationDays,
                status,
                new BigDecimal(averageRating),
                reviewCount,
                createdAt
        );
    }
}
