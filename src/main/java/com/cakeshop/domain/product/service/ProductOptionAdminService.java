package com.cakeshop.domain.product.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.cakeshop.domain.product.dto.form.ProductForm;
import com.cakeshop.domain.product.dto.form.ProductOptionForm;
import com.cakeshop.domain.product.dto.form.ProductOptionGroupForm;
import com.cakeshop.domain.product.dto.form.ProductOptionMoveDirection;
import com.cakeshop.domain.product.dto.view.ProductOptionAdminRow;
import com.cakeshop.domain.product.dto.view.ProductOptionAdminView;
import com.cakeshop.domain.product.dto.view.ProductOptionGroupAdminView;
import com.cakeshop.domain.product.dto.view.ProductOptionManagementView;
import com.cakeshop.domain.product.entity.Product;
import com.cakeshop.domain.product.entity.ProductOption;
import com.cakeshop.domain.product.entity.ProductOptionGroup;
import com.cakeshop.domain.product.entity.ProductOptionStatus;
import com.cakeshop.domain.product.entity.ProductStatus;
import com.cakeshop.domain.product.error.ProductErrorCode;
import com.cakeshop.domain.product.mapper.ProductMapper;
import com.cakeshop.global.error.BusinessException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductOptionAdminService {

    private final ProductMapper productMapper;

    public ProductOptionAdminService(ProductMapper productMapper) {
        this.productMapper = productMapper;
    }

    /** 상품의 비활성 항목을 포함한 옵션 관리 정보를 조회한다. */
    @Transactional(readOnly = true)
    public ProductOptionManagementView getOptions(long productId) {
        ProductForm product =
                productMapper.findAdminProductFormById(productId);

        if (product == null) {
            throw new BusinessException(
                    ProductErrorCode.NOT_FOUND
            );
        }

        List<ProductOptionAdminRow> rows =
                productMapper.findAdminOptionRowsByProductId(
                        productId
                );

        return new ProductOptionManagementView(
                productId,
                product.getName(),
                groupRows(rows)
        );
    }

    /** 새로운 옵션 그룹을 등록한다. */
    @Transactional
    public long createOptionGroup(
            long productId,
            ProductOptionGroupForm form
    ) {
        if (form == null) {
            throw new BusinessException(
                    ProductErrorCode.INVALID_OPTION_GROUP
            );
        }

        Product product = findProductForUpdate(productId);

        validateRequiredOptionGroupCreation(
                product,
                form
        );

        int sortOrder = groupRows(
                productMapper.findAdminOptionRowsByProductId(
                        productId
                )
        ).stream()
                .mapToInt(ProductOptionGroupAdminView::sortOrder)
                .max()
                .orElse(0) + 1;

        ProductOptionGroup optionGroup =
                toOptionGroup(
                        null,
                        productId,
                        form,
                        sortOrder
                );

        productMapper.insertOptionGroup(optionGroup);

        return optionGroup.getId();
    }

    /** 지정한 상품의 옵션 그룹과 활성 상태를 수정한다. */
    @Transactional
    public void updateOptionGroup(
            long productId,
            long optionGroupId,
            ProductOptionGroupForm form
    ) {
        if (form == null) {
            throw new BusinessException(
                    ProductErrorCode.INVALID_OPTION_GROUP
            );
        }

        Product product = findProductForUpdate(productId);
        List<ProductOptionAdminRow> rows =
                findOptionRowsForUpdate(
                        productId,
                        optionGroupId
                );

        validateRequiredOptionGroupUpdate(
                product,
                form,
                rows
        );

        ProductOptionGroup optionGroup =
                toOptionGroup(
                        optionGroupId,
                        productId,
                        form,
                        null
                );

        productMapper.updateOptionGroup(
                productId,
                optionGroup
        );
    }

    /** 옵션 그룹에 새로운 개별 옵션을 등록한다. */
    @Transactional
    public long createOption(
            long productId,
            long optionGroupId,
            ProductOptionForm form
    ) {
        if (form == null) {
            throw new BusinessException(
                    ProductErrorCode.INVALID_OPTION
            );
        }

        findProductForUpdate(productId);
        List<ProductOptionAdminRow> rows =
                findOptionRowsForUpdate(
                        productId,
                        optionGroupId
                );

        int sortOrder = rows
                .stream()
                .filter(row -> row.optionId() != null)
                .mapToInt(row -> row.optionSortOrder())
                .max()
                .orElse(0) + 1;

        ProductOption option =
                toProductOption(
                        null,
                        optionGroupId,
                        form,
                        sortOrder
                );

        productMapper.insertProductOption(option);

        return option.getId();
    }

    /** 지정한 상품과 그룹의 개별 옵션과 활성 상태를 수정한다. */
    @Transactional
    public void updateOption(
            long productId,
            long optionGroupId,
            long optionId,
            ProductOptionForm form
    ) {
        if (form == null) {
            throw new BusinessException(
                    ProductErrorCode.INVALID_OPTION
            );
        }

        Product product = findProductForUpdate(productId);
        List<ProductOptionAdminRow> rows =
                findOptionRowsForUpdate(
                        productId,
                        optionGroupId
                );
        ProductOptionAdminRow currentOption =
                findOptionRow(rows, optionId);

        validateRequiredOptionUpdate(
                product,
                form,
                rows,
                currentOption
        );

        ProductOption option =
                toProductOption(
                        optionId,
                        optionGroupId,
                        form,
                        null
                );

        productMapper.updateProductOption(
                productId,
                optionGroupId,
                option
        );
    }

    /** 옵션 그룹을 한 칸 위나 아래로 이동하고 순서를 연속 번호로 정리한다. */
    @Transactional
    public void moveOptionGroup(
            long productId,
            long optionGroupId,
            ProductOptionMoveDirection direction
    ) {
        Objects.requireNonNull(direction);
        findProductForUpdate(productId);

        List<ProductOptionGroupAdminView> groups =
                new ArrayList<>(
                        groupRows(
                                productMapper
                                        .findAdminOptionRowsByProductIdForUpdate(
                                                productId
                                        )
                        )
                );

        int currentIndex = findGroupIndex(
                groups,
                optionGroupId
        );
        int targetIndex = direction.targetIndex(currentIndex);

        if (targetIndex >= 0 && targetIndex < groups.size()) {
            Collections.swap(groups, currentIndex, targetIndex);
        }

        for (int index = 0; index < groups.size(); index++) {
            productMapper.updateOptionGroupSortOrder(
                    productId,
                    groups.get(index).id(),
                    index + 1
            );
        }
    }

    /** 개별 옵션을 한 칸 위나 아래로 이동하고 순서를 연속 번호로 정리한다. */
    @Transactional
    public void moveOption(
            long productId,
            long optionGroupId,
            long optionId,
            ProductOptionMoveDirection direction
    ) {
        Objects.requireNonNull(direction);
        findProductForUpdate(productId);

        List<ProductOptionAdminRow> rows =
                findOptionRowsForUpdate(
                        productId,
                        optionGroupId
                );

        findOptionRow(rows, optionId);

        List<ProductOptionAdminView> options =
                new ArrayList<>(
                        groupRows(rows)
                                .getFirst()
                                .options()
                );

        int currentIndex = findOptionIndex(options, optionId);
        int targetIndex = direction.targetIndex(currentIndex);

        if (targetIndex >= 0 && targetIndex < options.size()) {
            Collections.swap(options, currentIndex, targetIndex);
        }

        for (int index = 0; index < options.size(); index++) {
            productMapper.updateProductOptionSortOrder(
                    productId,
                    optionGroupId,
                    options.get(index).id(),
                    index + 1
            );
        }
    }

    private List<ProductOptionGroupAdminView> groupRows(
            List<ProductOptionAdminRow> rows
    ) {
        Map<Long, ProductOptionGroupAdminView> groups =
                new LinkedHashMap<>();

        for (ProductOptionAdminRow row : rows) {
            ProductOptionGroupAdminView group =
                    groups.computeIfAbsent(
                            row.groupId(),
                            ignored ->
                                    new ProductOptionGroupAdminView(
                                            row.groupId(),
                                            row.groupName(),
                                            row.required(),
                                            row.selectionType(),
                                            row.groupStatus(),
                                            row.groupSortOrder(),
                                            new ArrayList<>()
                                    )
                    );

            if (row.optionId() != null) {
                group.options().add(
                        new ProductOptionAdminView(
                                row.optionId(),
                                row.optionName(),
                                row.additionalPrice(),
                                row.optionStatus(),
                                row.optionSortOrder()
                        )
                );
            }
        }

        return groups.values().stream()
                .map(group ->
                        new ProductOptionGroupAdminView(
                                group.id(),
                                group.name(),
                                group.required(),
                                group.selectionType(),
                                group.status(),
                                group.sortOrder(),
                                List.copyOf(group.options())
                        )
                )
                .toList();
    }

    private ProductOptionGroup toOptionGroup(
            Long optionGroupId,
            long productId,
            ProductOptionGroupForm form,
            Integer sortOrder
    ) {
        ProductOptionGroup optionGroup =
                new ProductOptionGroup();

        optionGroup.setId(optionGroupId);
        optionGroup.setProductId(productId);
        optionGroup.setName(form.normalizedName());
        optionGroup.setRequired(form.isRequired());
        optionGroup.setSelectionType(
                form.getSelectionType()
        );
        optionGroup.setStatus(form.getStatus());
        optionGroup.setSortOrder(sortOrder);

        return optionGroup;
    }

    private ProductOption toProductOption(
            Long optionId,
            long optionGroupId,
            ProductOptionForm form,
            Integer sortOrder
    ) {
        ProductOption option = new ProductOption();

        option.setId(optionId);
        option.setOptionGroupId(optionGroupId);
        option.setName(form.normalizedName());
        option.setAdditionalPrice(
                form.getAdditionalPrice()
        );
        option.setStatus(form.getStatus());
        option.setSortOrder(sortOrder);

        return option;
    }

    private int findGroupIndex(
            List<ProductOptionGroupAdminView> groups,
            long optionGroupId
    ) {
        for (int index = 0; index < groups.size(); index++) {
            if (groups.get(index).id() == optionGroupId) {
                return index;
            }
        }

        throw new BusinessException(
                ProductErrorCode.OPTION_GROUP_NOT_FOUND
        );
    }

    private int findOptionIndex(
            List<ProductOptionAdminView> options,
            long optionId
    ) {
        for (int index = 0; index < options.size(); index++) {
            if (options.get(index).id() == optionId) {
                return index;
            }
        }

        throw new BusinessException(
                ProductErrorCode.OPTION_NOT_FOUND
        );
    }

    private Product findProductForUpdate(long productId) {
        Product product =
                productMapper.findSalesInfoByIdForUpdate(
                        productId
                );

        if (product == null) {
            throw new BusinessException(
                    ProductErrorCode.NOT_FOUND
            );
        }

        return product;
    }

    private List<ProductOptionAdminRow>
            findOptionRowsForUpdate(
                    long productId,
                    long optionGroupId
            ) {
        List<ProductOptionAdminRow> rows =
                productMapper
                        .findAdminOptionRowsByGroupIdForUpdate(
                                productId,
                                optionGroupId
                        );

        if (rows.isEmpty()) {
            throw new BusinessException(
                    ProductErrorCode.OPTION_GROUP_NOT_FOUND
            );
        }

        return rows;
    }

    private ProductOptionAdminRow findOptionRow(
            List<ProductOptionAdminRow> rows,
            long optionId
    ) {
        return rows.stream()
                .filter(row -> row.optionId() != null)
                .filter(row -> row.optionId() == optionId)
                .findFirst()
                .orElseThrow(() ->
                        new BusinessException(
                                ProductErrorCode.OPTION_NOT_FOUND
                        )
                );
    }

    private void validateRequiredOptionGroupUpdate(
            Product product,
            ProductOptionGroupForm form,
            List<ProductOptionAdminRow> rows
    ) {
        if (product.getStatus() != ProductStatus.ACTIVE) {
            return;
        }

        ProductOptionAdminRow currentGroup = rows.getFirst();
        boolean deactivatesCurrentRequiredGroup =
                currentGroup.required()
                        && currentGroup.groupStatus()
                        == ProductOptionStatus.ACTIVE
                        && form.getStatus()
                        == ProductOptionStatus.INACTIVE;
        boolean hasActiveOption = rows.stream()
                .anyMatch(this::isActiveOption);
        boolean createsRequiredGroupWithoutActiveOption =
                form.isRequired()
                        && form.getStatus()
                        == ProductOptionStatus.ACTIVE
                        && !hasActiveOption;

        if (deactivatesCurrentRequiredGroup
                || createsRequiredGroupWithoutActiveOption) {
            throwRequiredOptionGroupEmpty();
        }
    }

    private void validateRequiredOptionGroupCreation(
            Product product,
            ProductOptionGroupForm form
    ) {
        if (product.getStatus() == ProductStatus.ACTIVE
                && form.isRequired()
                && form.getStatus()
                == ProductOptionStatus.ACTIVE) {
            throwRequiredOptionGroupEmpty();
        }
    }

    private void validateRequiredOptionUpdate(
            Product product,
            ProductOptionForm form,
            List<ProductOptionAdminRow> rows,
            ProductOptionAdminRow currentOption
    ) {
        ProductOptionAdminRow group = rows.getFirst();

        if (product.getStatus() != ProductStatus.ACTIVE
                || !group.required()
                || group.groupStatus()
                != ProductOptionStatus.ACTIVE
                || currentOption.optionStatus()
                != ProductOptionStatus.ACTIVE
                || form.getStatus()
                != ProductOptionStatus.INACTIVE) {
            return;
        }

        long activeOptionCount = rows.stream()
                .filter(this::isActiveOption)
                .count();

        if (activeOptionCount == 1) {
            throwRequiredOptionGroupEmpty();
        }
    }

    private boolean isActiveOption(
            ProductOptionAdminRow row
    ) {
        return row.optionId() != null
                && row.optionStatus()
                == ProductOptionStatus.ACTIVE;
    }

    private void throwRequiredOptionGroupEmpty() {
        throw new BusinessException(
                ProductErrorCode.REQUIRED_OPTION_GROUP_EMPTY
        );
    }

}
