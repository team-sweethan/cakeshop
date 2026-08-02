package com.cakeshop.domain.product.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import com.cakeshop.domain.product.dto.form.AdminStockFilter;
import com.cakeshop.domain.product.dto.form.ProductAdminSearchCondition;
import com.cakeshop.domain.product.dto.form.ProductForm;
import com.cakeshop.domain.product.dto.view.ProductAdminListView;
import com.cakeshop.domain.product.dto.view.ProductCategoryOptionView;
import com.cakeshop.domain.product.dto.form.ProductSearchCondition;
import com.cakeshop.domain.product.dto.form.ProductSort;
import com.cakeshop.domain.product.dto.form.StockFilter;
import com.cakeshop.domain.product.dto.view.ProductListView;
import com.cakeshop.domain.product.entity.Product;
import com.cakeshop.domain.product.entity.ProductStatus;
import com.cakeshop.domain.product.entity.ProductType;
import com.cakeshop.global.config.MariaDbIntegrationTest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.jdbc.core.JdbcTemplate;

@MybatisTest
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
                "A 일반 재고 상품",
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
                0,
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
                0,
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
                keyword + " A 일반 재고 상품"
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
                        keyword + " A 일반 재고 상품",
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
    void typeAndStockFiltersCanBeCombined() {
        ProductSearchCondition condition =
                baseCondition();

        condition.setMaxPrice(BigDecimal.valueOf(200_000));
        condition.setType(ProductType.GENERAL);
        condition.setStock(StockFilter.AVAILABLE);

        List<ProductListView> products =
                productMapper.findPublicProducts(
                        condition,
                        10,
                        0
                );

        assertThat(products)
                .extracting(ProductListView::name)
                .containsExactly(
                        keyword + " A 일반 재고 상품",
                        keyword + " E 최대 가격 상품"
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
                        keyword + " A 일반 재고 상품",
                        keyword + " B 품절 상품",
                        keyword + " E 최대 가격 상품"
                );
    }

    @Test
    void findSalesInfoById_existingProduct_returnsSalesFields() {
        Long productId = jdbcTemplate.queryForObject(
                """
                SELECT id
                FROM products
                WHERE name = ?
                """,
                Long.class,
                keyword + " D 판매 중지 상품"
        );

        Product product =
                productMapper.findSalesInfoById(productId);

        assertThat(product).isNotNull();
        assertThat(product.getId()).isEqualTo(productId);
        assertThat(product.getName())
                .isEqualTo(
                        keyword + " D 판매 중지 상품"
                );
        assertThat(product.getProductType())
                .isEqualTo(ProductType.GENERAL);
        assertThat(product.getPreparationDays()).isZero();
        assertThat(product.getBasePrice())
                .isEqualByComparingTo("40000");
        assertThat(product.getStockQuantity()).isEqualTo(5);
        assertThat(product.getStatus())
                .isEqualTo(ProductStatus.INACTIVE);
    }

    @Test
    void findSalesInfoById_missingProduct_returnsNull() {
        Product product =
                productMapper.findSalesInfoById(Long.MAX_VALUE);

        assertThat(product).isNull();
    }

    @Test
    void findSalesInfoByIdForUpdate_existingProduct_returnsLockedSalesInfo() {
        Long productId = jdbcTemplate.queryForObject(
                """
                SELECT id
                FROM products
                WHERE name = ?
                """,
                Long.class,
                keyword + " A 일반 재고 상품"
        );

        Product product =
                productMapper.findSalesInfoByIdForUpdate(
                        productId
                );

        assertThat(product).isNotNull();
        assertThat(product.getId()).isEqualTo(productId);
        assertThat(product.getProductType())
                .isEqualTo(ProductType.GENERAL);
        assertThat(product.getStockQuantity()).isEqualTo(10);
        assertThat(product.getStatus())
                .isEqualTo(ProductStatus.ACTIVE);
    }

    @Test
    void decreaseStockIfAvailable_requestsExceedStock_neverMakesStockNegative() {
        Long productId = jdbcTemplate.queryForObject(
                """
                SELECT id
                FROM products
                WHERE name = ?
                """,
                Long.class,
                keyword + " A 일반 재고 상품"
        );

        int firstUpdate =
                productMapper.decreaseStockIfAvailable(
                        productId,
                        7
                );
        int secondUpdate =
                productMapper.decreaseStockIfAvailable(
                        productId,
                        7
                );

        assertThat(firstUpdate).isEqualTo(1);
        assertThat(secondUpdate).isZero();
        assertThat(jdbcTemplate.queryForObject(
                """
                SELECT stock_quantity
                FROM products
                WHERE id = ?
                """,
                Integer.class,
                productId
        )).isEqualTo(3);
    }

    @Test
    void decreaseStockIfAvailable_customProduct_doesNotChangeStock() {
        Long productId = jdbcTemplate.queryForObject(
                """
                SELECT id
                FROM products
                WHERE name = ?
                """,
                Long.class,
                keyword + " C 인기 주문 제작"
        );

        int updatedRows =
                productMapper.decreaseStockIfAvailable(
                        productId,
                        1
                );

        assertThat(updatedRows).isZero();
        assertThat(jdbcTemplate.queryForObject(
                """
                SELECT stock_quantity
                FROM products
                WHERE id = ?
                """,
                Integer.class,
                productId
        )).isNull();
    }

    @Test
    void restoreLimitedStock_inactiveGeneralProduct_restoresStock() {
        Long productId = jdbcTemplate.queryForObject(
                """
                SELECT id
                FROM products
                WHERE name = ?
                """,
                Long.class,
                keyword + " D 판매 중지 상품"
        );

        int updatedRows =
                productMapper.restoreLimitedStock(
                        productId,
                        3
                );

        assertThat(updatedRows).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                """
                SELECT stock_quantity
                FROM products
                WHERE id = ?
                """,
                Integer.class,
                productId
        )).isEqualTo(8);
    }

    @Test
    void restoreLimitedStock_productChangedToCustom_restoresPreviouslyDeductedStock() {
        Long productId = jdbcTemplate.queryForObject(
                """
                SELECT id
                FROM products
                WHERE name = ?
                """,
                Long.class,
                keyword + " D 판매 중지 상품"
        );
        jdbcTemplate.update(
                """
                UPDATE products
                SET product_type = 'CUSTOM',
                    preparation_days = 1
                WHERE id = ?
                """,
                productId
        );

        int updatedRows =
                productMapper.restoreLimitedStock(
                        productId,
                        3
                );

        assertThat(updatedRows).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                """
                SELECT stock_quantity
                FROM products
                WHERE id = ?
                """,
                Integer.class,
                productId
        )).isEqualTo(8);
    }

    @Test
    void restoreLimitedStock_stockChangedToUnlimited_doesNotInventFiniteStock() {
        Long productId = jdbcTemplate.queryForObject(
                """
                SELECT id
                FROM products
                WHERE name = ?
                """,
                Long.class,
                keyword + " D 판매 중지 상품"
        );
        jdbcTemplate.update(
                """
                UPDATE products
                SET stock_quantity = NULL
                WHERE id = ?
                """,
                productId
        );

        int updatedRows =
                productMapper.restoreLimitedStock(
                        productId,
                        3
                );

        assertThat(updatedRows).isZero();
        assertThat(jdbcTemplate.queryForObject(
                """
                SELECT stock_quantity
                FROM products
                WHERE id = ?
                """,
                Integer.class,
                productId
        )).isNull();
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
                    status,
                    average_rating,
                    review_count,
                    created_at
                )
                VALUES (?, ?, '', ?, ?, ?, ?, ?, ?, ?, ?)
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

    @Test
    void adminListIncludesActiveAndInactiveProducts() {
        ProductAdminSearchCondition condition =
                new ProductAdminSearchCondition();

        Long expectedCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM products",
                Long.class
        );

        List<ProductAdminListView> products =
                productMapper.findAdminProducts(
                        condition,
                        expectedCount.intValue(),
                        0
                );

        assertThat(
                productMapper.countAdminProducts(condition)
        ).isEqualTo(expectedCount);

        List<ProductAdminListView> testProducts =
                products.stream()
                        .filter(product ->
                                product.name().startsWith(keyword)
                        )
                        .toList();

        assertThat(testProducts)
                .extracting(ProductAdminListView::name)
                .containsExactly(
                        keyword + " F 10만원 초과 주문 제작",
                        keyword + " E 최대 가격 상품",
                        keyword + " D 판매 중지 상품",
                        keyword + " C 인기 주문 제작",
                        keyword + " B 품절 상품",
                        keyword + " A 일반 재고 상품"
                );

        assertThat(testProducts)
                .anyMatch(product ->
                        product.status()
                                == ProductStatus.INACTIVE
                );
    }

    @Test
    void adminSearchConditionsCanBeCombined() {
        ProductAdminSearchCondition condition =
                new ProductAdminSearchCondition();

        condition.setKeyword(keyword + " B 품절");
        condition.setType(ProductType.GENERAL);
        condition.setStatus(ProductStatus.ACTIVE);
        condition.setStock(
                AdminStockFilter.OUT_OF_STOCK
        );

        assertThat(
                productMapper.countAdminProducts(condition)
        ).isEqualTo(1);

        List<ProductAdminListView> products =
                productMapper.findAdminProducts(
                        condition,
                        10,
                        0
                );

        assertThat(products)
                .extracting(ProductAdminListView::name)
                .containsExactly(
                        keyword + " B 품절 상품"
                );
    }

    @Test
    void adminCanChangeProductStatus() {
        Long productId = jdbcTemplate.queryForObject(
                """
                SELECT id
                FROM products
                WHERE name = ?
                """,
                Long.class,
                keyword + " D 판매 중지 상품"
        );

        LocalDateTime previousUpdatedAt =
                LocalDateTime.of(2000, 1, 1, 0, 0);

        jdbcTemplate.update(
                """
                UPDATE products
                SET updated_at = ?
                WHERE id = ?
                """,
                previousUpdatedAt,
                productId
        );

        // 판매 중지 상품의 상태를 판매 중으로 변경한다.
        int updatedRows =
                productMapper.updateProductStatus(
                        productId,
                        ProductStatus.ACTIVE
                );

        String updatedStatus =
                jdbcTemplate.queryForObject(
                        """
                        SELECT status
                        FROM products
                        WHERE id = ?
                        """,
                        String.class,
                        productId
                );

        LocalDateTime actualUpdatedAt =
                jdbcTemplate.queryForObject(
                        """
                        SELECT updated_at
                        FROM products
                        WHERE id = ?
                        """,
                        LocalDateTime.class,
                        productId
                );

        // 상태 변경과 함께 DB가 수정 시간을 자동으로 갱신했는지 확인한다.
        assertThat(updatedRows).isEqualTo(1);
        assertThat(updatedStatus).isEqualTo("ACTIVE");
        assertThat(actualUpdatedAt).isAfter(previousUpdatedAt);
    }

    @Test
    void changingStatusOfMissingProductReturnsZero() {
        int updatedRows =
                productMapper.updateProductStatus(
                        Long.MAX_VALUE,
                        ProductStatus.INACTIVE
                );

        // 존재하지 않는 상품은 변경된 행이 없어야 한다.
        assertThat(updatedRows).isZero();
    }

    @Test
    void activeCategoriesCanBeQueried() {
        List<ProductCategoryOptionView> categories =
                productMapper.findActiveCategories();

        // setUp에서 추가한 활성 카테고리가 목록에 포함되는지 확인한다.
        assertThat(categories)
                .anyMatch(category ->
                        category.id().equals(categoryId)
                );

        // 활성 카테고리의 존재 여부가 true인지 확인한다.
        assertThat(
                productMapper.existsActiveCategoryById(categoryId)
        ).isTrue();
    }

    @Test
    void inactiveCategoryIsNotAvailable() {
        // 조회하기 전에 카테고리를 비활성화한다.
        jdbcTemplate.update(
                """
                UPDATE categories
                SET is_active = 0
                WHERE id = ?
                """,
                categoryId
        );

        // 비활성 카테고리는 선택 가능한 카테고리로 판단하지 않아야 한다.
        assertThat(
                productMapper.existsActiveCategoryById(categoryId)
        ).isFalse();

        List<ProductCategoryOptionView> categories =
                productMapper.findActiveCategories();

        // 등록 화면의 카테고리 목록에서도 제외되는지 확인한다.
        assertThat(categories)
                .noneMatch(category ->
                        category.id().equals(categoryId)
                );
    }

    @Test
    void adminCanInsertInactiveProduct() {
        Product product = new Product();

        product.setCategoryId(categoryId);
        product.setName(keyword + " 신규 상품");
        product.setDescription("상품 등록 테스트");
        product.setBasePrice(
                BigDecimal.valueOf(45_000)
        );
        product.setStockQuantity(10);
        product.setProductType(ProductType.GENERAL);
        product.setPreparationDays(0);
        product.setStatus(ProductStatus.INACTIVE);

        int insertedRows =
                productMapper.insertProduct(product);

        // 상품 한 건이 등록되고 ID가 생성됐는지 확인한다.
        assertThat(insertedRows).isEqualTo(1);
        assertThat(product.getId()).isNotNull();

        String status = jdbcTemplate.queryForObject(
                """
                SELECT status
                FROM products
                WHERE id = ?
                """,
                String.class,
                product.getId()
        );

        assertThat(status).isEqualTo("INACTIVE");
    }

    @Test
    void adminProductFormContainsExistingValues() {
        ProductForm form =
                productMapper.findAdminProductFormById(
                        optionProductId
                );

        assertThat(form).isNotNull();
        assertThat(form.getCategoryId())
                .isEqualTo(categoryId);
        assertThat(form.getName())
                .isEqualTo(
                        keyword + " A 일반 재고 상품"
                );
        assertThat(form.getDescription())
                .isEmpty();
        assertThat(form.getBasePrice())
                .isEqualByComparingTo("10000");
        assertThat(form.getStockQuantity())
                .isEqualTo(10);
        assertThat(form.getOriginalStockQuantity())
                .isEqualTo(10);
        assertThat(form.getProductType())
                .isEqualTo(ProductType.GENERAL);
        assertThat(form.getPreparationDays())
                .isZero();
    }

    @Test
    void adminCanUpdateProductWithoutChangingStatus() {
        ProductForm originalForm =
                productMapper.findAdminProductFormById(
                        optionProductId
                );
        Product product = new Product();

        product.setId(optionProductId);
        product.setCategoryId(categoryId);
        product.setName(keyword + " 수정 상품");
        product.setDescription("수정된 상품 설명");
        product.setBasePrice(
                BigDecimal.valueOf(55_000)
        );
        product.setStockQuantity(null);
        product.setProductType(ProductType.CUSTOM);
        product.setPreparationDays(3);

        LocalDateTime previousUpdatedAt =
                LocalDateTime.of(2000, 1, 1, 0, 0);

        jdbcTemplate.update(
                """
                UPDATE products
                SET updated_at = ?
                WHERE id = ?
                """,
                previousUpdatedAt,
                optionProductId
        );

        int updatedRows =
                productMapper.updateProduct(
                        product,
                        originalForm.getOriginalStockQuantity()
                );

        ProductForm updatedForm =
                productMapper.findAdminProductFormById(
                        optionProductId
                );

        String status = jdbcTemplate.queryForObject(
                """
                SELECT status
                FROM products
                WHERE id = ?
                """,
                String.class,
                optionProductId
        );

        LocalDateTime actualUpdatedAt =
                jdbcTemplate.queryForObject(
                        """
                        SELECT updated_at
                        FROM products
                        WHERE id = ?
                        """,
                        LocalDateTime.class,
                        optionProductId
                );

        assertThat(updatedRows).isEqualTo(1);
        assertThat(updatedForm).isNotNull();
        assertThat(updatedForm.getName())
                .isEqualTo(keyword + " 수정 상품");
        assertThat(updatedForm.getDescription())
                .isEqualTo("수정된 상품 설명");
        assertThat(updatedForm.getBasePrice())
                .isEqualByComparingTo("55000");
        assertThat(updatedForm.getStockQuantity())
                .isNull();
        assertThat(updatedForm.getProductType())
                .isEqualTo(ProductType.CUSTOM);
        assertThat(updatedForm.getPreparationDays())
                .isEqualTo(3);

        // 기본 정보 수정 후에도 기존 판매 상태는 유지되어야 한다.
        assertThat(status).isEqualTo("ACTIVE");
        assertThat(actualUpdatedAt).isAfter(previousUpdatedAt);
    }

    @Test
    void adminUpdateWithStaleStock_doesNotOverwritePaymentDeduction() {
        ProductForm staleForm =
                productMapper.findAdminProductFormById(
                        optionProductId
                );
        int deductedRows =
                productMapper.decreaseStockIfAvailable(
                        optionProductId,
                        3
                );

        Product product = new Product();

        product.setId(optionProductId);
        product.setCategoryId(staleForm.getCategoryId());
        product.setName(staleForm.getName());
        product.setDescription(staleForm.getDescription());
        product.setBasePrice(staleForm.getBasePrice());
        product.setStockQuantity(
                staleForm.getStockQuantity()
        );
        product.setProductType(staleForm.getProductType());
        product.setPreparationDays(
                staleForm.getPreparationDays()
        );

        int updatedRows =
                productMapper.updateProduct(
                        product,
                        staleForm.getOriginalStockQuantity()
                );

        Integer currentStock = jdbcTemplate.queryForObject(
                """
                SELECT stock_quantity
                FROM products
                WHERE id = ?
                """,
                Integer.class,
                optionProductId
        );

        assertThat(deductedRows).isEqualTo(1);
        assertThat(updatedRows).isZero();
        assertThat(currentStock).isEqualTo(7);
    }

    @Test
    void missingProductCannotBeReadOrUpdated() {
        assertThat(
                productMapper.findAdminProductFormById(
                        Long.MAX_VALUE
                )
        ).isNull();

        Product product = new Product();

        product.setId(Long.MAX_VALUE);
        product.setCategoryId(categoryId);
        product.setName(keyword + " 존재하지 않는 상품");
        product.setDescription(null);
        product.setBasePrice(BigDecimal.ZERO);
        product.setStockQuantity(null);
        product.setProductType(ProductType.GENERAL);
        product.setPreparationDays(0);

        assertThat(
                productMapper.updateProduct(
                        product,
                        null
                )
        ).isZero();
    }
}
