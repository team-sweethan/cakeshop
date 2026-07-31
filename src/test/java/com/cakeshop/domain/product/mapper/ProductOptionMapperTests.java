package com.cakeshop.domain.product.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import com.cakeshop.domain.product.admin.dto.view.ProductOptionAdminRow;
import com.cakeshop.domain.product.customer.dto.view.ProductOptionRow;
import com.cakeshop.domain.product.entity.ProductOption;
import com.cakeshop.domain.product.entity.ProductOptionGroup;
import com.cakeshop.domain.product.entity.ProductOptionSelectionType;
import com.cakeshop.domain.product.entity.ProductOptionStatus;
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
class ProductOptionMapperTests {

    @Autowired
    private ProductMapper productMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private long productId;

    @BeforeEach
    void setUp() {
        String suffix = Long.toString(System.nanoTime());

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
                "OPTION_TEST_" + suffix,
                "옵션 테스트"
        );

        Long categoryId = jdbcTemplate.queryForObject(
                "SELECT id FROM categories WHERE code = ?",
                Long.class,
                "OPTION_TEST_" + suffix
        );

        jdbcTemplate.update(
                """
                INSERT INTO products (
                    category_id,
                    name,
                    base_price,
                    stock_quantity,
                    product_type,
                    preparation_days,
                    status
                )
                VALUES (?, ?, 35000, 10, 'GENERAL', 0, 'ACTIVE')
                """,
                categoryId,
                "옵션 테스트 상품 " + suffix
        );

        productId = jdbcTemplate.queryForObject(
                "SELECT id FROM products WHERE name = ?",
                Long.class,
                "옵션 테스트 상품 " + suffix
        );
    }

    @Test
    void findAdminOptionRowsByProductId_inactiveItems_returnsAllInOrder() {
        long secondGroupId = insertGroup(
                "맛",
                ProductOptionStatus.INACTIVE,
                2
        );
        long firstGroupId = insertGroup(
                "크기",
                ProductOptionStatus.ACTIVE,
                1
        );

        insertOption(
                secondGroupId,
                "딸기",
                ProductOptionStatus.ACTIVE,
                1
        );
        insertOption(
                firstGroupId,
                "2호",
                ProductOptionStatus.INACTIVE,
                2
        );
        insertOption(
                firstGroupId,
                "1호",
                ProductOptionStatus.ACTIVE,
                1
        );

        List<ProductOptionAdminRow> rows =
                productMapper.findAdminOptionRowsByProductId(
                        productId
                );

        assertThat(rows)
                .extracting(ProductOptionAdminRow::groupName)
                .containsExactly("크기", "크기", "맛");
        assertThat(rows)
                .extracting(ProductOptionAdminRow::optionName)
                .containsExactly("1호", "2호", "딸기");
        assertThat(rows.get(1).optionStatus())
                .isEqualTo(ProductOptionStatus.INACTIVE);
        assertThat(rows.get(2).groupStatus())
                .isEqualTo(ProductOptionStatus.INACTIVE);
    }

    @Test
    void findAdminOptionRowsByGroupIdForUpdate_targetGroup_returnsAllOptions() {
        long targetGroupId = insertGroup(
                "크기",
                ProductOptionStatus.ACTIVE,
                1
        );
        long otherGroupId = insertGroup(
                "맛",
                ProductOptionStatus.ACTIVE,
                2
        );

        insertOption(
                targetGroupId,
                "2호",
                ProductOptionStatus.INACTIVE,
                2
        );
        insertOption(
                targetGroupId,
                "1호",
                ProductOptionStatus.ACTIVE,
                1
        );
        insertOption(
                otherGroupId,
                "초코",
                ProductOptionStatus.ACTIVE,
                1
        );

        List<ProductOptionAdminRow> rows =
                productMapper
                        .findAdminOptionRowsByGroupIdForUpdate(
                                productId,
                                targetGroupId
                        );

        assertThat(rows)
                .extracting(ProductOptionAdminRow::groupId)
                .containsOnly(targetGroupId);
        assertThat(rows)
                .extracting(ProductOptionAdminRow::optionName)
                .containsExactly("1호", "2호");
        assertThat(rows)
                .extracting(ProductOptionAdminRow::optionStatus)
                .containsExactly(
                        ProductOptionStatus.ACTIVE,
                        ProductOptionStatus.INACTIVE
                );
    }

    @Test
    void findAdminOptionRowsByProductIdForUpdate_returnsAllGroupsAndOptions() {
        long requiredGroupId = insertGroup(
                "크기",
                ProductOptionStatus.ACTIVE,
                1
        );
        long optionalGroupId = insertGroup(
                "맛",
                ProductOptionStatus.INACTIVE,
                2
        );

        insertOption(
                requiredGroupId,
                "1호",
                ProductOptionStatus.ACTIVE,
                1
        );
        insertOption(
                optionalGroupId,
                "초코",
                ProductOptionStatus.INACTIVE,
                1
        );

        List<ProductOptionAdminRow> rows =
                productMapper
                        .findAdminOptionRowsByProductIdForUpdate(
                                productId
                        );

        assertThat(rows)
                .extracting(ProductOptionAdminRow::groupId)
                .containsExactly(
                        requiredGroupId,
                        optionalGroupId
                );
        assertThat(rows)
                .extracting(ProductOptionAdminRow::optionName)
                .containsExactly("1호", "초코");
    }

    @Test
    void insertAndUpdateOptionGroup_ownedByProduct_persistsChanges() {
        ProductOptionGroup optionGroup =
                new ProductOptionGroup();

        optionGroup.setProductId(productId);
        optionGroup.setName("크기");
        optionGroup.setRequired(true);
        optionGroup.setSelectionType(
                ProductOptionSelectionType.SINGLE
        );
        optionGroup.setStatus(ProductOptionStatus.ACTIVE);
        optionGroup.setSortOrder(1);

        assertThat(
                productMapper.insertOptionGroup(optionGroup)
        ).isEqualTo(1);
        assertThat(optionGroup.getId()).isNotNull();

        optionGroup.setName("사이즈");
        optionGroup.setRequired(false);
        optionGroup.setStatus(ProductOptionStatus.INACTIVE);

        assertThat(productMapper.updateOptionGroup(
                productId,
                optionGroup
        )).isEqualTo(1);
        assertThat(productMapper.updateOptionGroupSortOrder(
                productId,
                optionGroup.getId(),
                3
        )).isEqualTo(1);

        ProductOptionAdminRow row =
                productMapper
                        .findAdminOptionRowsByProductId(productId)
                        .getFirst();

        assertThat(row.groupName()).isEqualTo("사이즈");
        assertThat(row.required()).isFalse();
        assertThat(row.groupStatus())
                .isEqualTo(ProductOptionStatus.INACTIVE);
        assertThat(row.groupSortOrder()).isEqualTo(3);
        assertThat(row.optionId()).isNull();
    }

    @Test
    void insertAndUpdateProductOption_ownedByGroup_persistsChanges() {
        long optionGroupId = insertGroup(
                "크기",
                ProductOptionStatus.ACTIVE,
                1
        );

        ProductOption option = new ProductOption();

        option.setOptionGroupId(optionGroupId);
        option.setName("1호");
        option.setAdditionalPrice(BigDecimal.ZERO);
        option.setStatus(ProductOptionStatus.ACTIVE);
        option.setSortOrder(1);

        assertThat(
                productMapper.insertProductOption(option)
        ).isEqualTo(1);
        assertThat(option.getId()).isNotNull();

        option.setName("2호");
        option.setAdditionalPrice(
                BigDecimal.valueOf(10_000)
        );
        option.setStatus(ProductOptionStatus.INACTIVE);

        assertThat(productMapper.updateProductOption(
                productId,
                optionGroupId,
                option
        )).isEqualTo(1);
        assertThat(productMapper.updateProductOptionSortOrder(
                productId,
                optionGroupId,
                option.getId(),
                2
        )).isEqualTo(1);

        ProductOptionAdminRow row =
                productMapper
                        .findAdminOptionRowsByProductId(productId)
                        .getFirst();

        assertThat(row.optionName()).isEqualTo("2호");
        assertThat(row.additionalPrice())
                .isEqualByComparingTo("10000");
        assertThat(row.optionStatus())
                .isEqualTo(ProductOptionStatus.INACTIVE);
        assertThat(row.optionSortOrder()).isEqualTo(2);
    }

    @Test
    void findPublicOptionRows_inactiveGroupAndOption_excludesBoth() {
        long activeGroupId = insertGroup(
                "크기",
                ProductOptionStatus.ACTIVE,
                1
        );
        long inactiveGroupId = insertGroup(
                "맛",
                ProductOptionStatus.INACTIVE,
                2
        );

        insertOption(
                activeGroupId,
                "1호",
                ProductOptionStatus.ACTIVE,
                1
        );
        insertOption(
                activeGroupId,
                "단종 크기",
                ProductOptionStatus.INACTIVE,
                2
        );
        insertOption(
                inactiveGroupId,
                "초코",
                ProductOptionStatus.ACTIVE,
                1
        );

        List<ProductOptionRow> rows =
                productMapper.findPublicOptionRowsByProductId(
                        productId
                );

        assertThat(rows)
                .extracting(ProductOptionRow::optionName)
                .containsExactly("1호");
    }

    @Test
    void ownershipChecks_otherProduct_returnsFalse() {
        long optionGroupId = insertGroup(
                "크기",
                ProductOptionStatus.ACTIVE,
                1
        );
        long optionId = insertOption(
                optionGroupId,
                "1호",
                ProductOptionStatus.ACTIVE,
                1
        );

        assertThat(productMapper.existsOptionGroupById(
                Long.MAX_VALUE,
                optionGroupId
        )).isFalse();
        assertThat(productMapper.existsProductOptionById(
                Long.MAX_VALUE,
                optionGroupId,
                optionId
        )).isFalse();
        assertThat(productMapper.updateOptionGroupSortOrder(
                Long.MAX_VALUE,
                optionGroupId,
                2
        )).isZero();
        assertThat(productMapper.updateProductOptionSortOrder(
                Long.MAX_VALUE,
                optionGroupId,
                optionId,
                2
        )).isZero();
    }

    private long insertGroup(
            String name,
            ProductOptionStatus status,
            int sortOrder
    ) {
        ProductOptionGroup optionGroup =
                new ProductOptionGroup();

        optionGroup.setProductId(productId);
        optionGroup.setName(name);
        optionGroup.setRequired(true);
        optionGroup.setSelectionType(
                ProductOptionSelectionType.SINGLE
        );
        optionGroup.setStatus(status);
        optionGroup.setSortOrder(sortOrder);

        productMapper.insertOptionGroup(optionGroup);

        return optionGroup.getId();
    }

    private long insertOption(
            long optionGroupId,
            String name,
            ProductOptionStatus status,
            int sortOrder
    ) {
        ProductOption option = new ProductOption();

        option.setOptionGroupId(optionGroupId);
        option.setName(name);
        option.setAdditionalPrice(BigDecimal.ZERO);
        option.setStatus(status);
        option.setSortOrder(sortOrder);

        productMapper.insertProductOption(option);

        return option.getId();
    }
}
