package com.cakeshop.domain.product.admin.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.cakeshop.domain.product.admin.dto.form.ProductForm;
import com.cakeshop.domain.product.admin.dto.form.ProductOptionForm;
import com.cakeshop.domain.product.admin.dto.form.ProductOptionGroupForm;
import com.cakeshop.domain.product.admin.dto.form.ProductOptionMoveDirection;
import com.cakeshop.domain.product.admin.dto.view.ProductOptionAdminRow;
import com.cakeshop.domain.product.admin.dto.view.ProductOptionAdminView;
import com.cakeshop.domain.product.admin.dto.view.ProductOptionGroupAdminView;
import com.cakeshop.domain.product.admin.dto.view.ProductOptionManagementView;
import com.cakeshop.domain.product.entity.ProductOption;
import com.cakeshop.domain.product.entity.ProductOptionGroup;
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
        ensureProductExists(productId);

        if (form == null) {
            throw new BusinessException(
                    ProductErrorCode.INVALID_OPTION_GROUP
            );
        }

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
        ensureOptionGroupExists(productId, optionGroupId);

        if (form == null) {
            throw new BusinessException(
                    ProductErrorCode.INVALID_OPTION_GROUP
            );
        }

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
        ensureOptionGroupExists(productId, optionGroupId);

        if (form == null) {
            throw new BusinessException(
                    ProductErrorCode.INVALID_OPTION
            );
        }

        ProductOptionGroupAdminView optionGroup =
                findOptionGroup(
                        productId,
                        optionGroupId
                );

        int sortOrder = optionGroup.options()
                .stream()
                .mapToInt(ProductOptionAdminView::sortOrder)
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
        if (!productMapper.existsProductOptionById(
                productId,
                optionGroupId,
                optionId
        )) {
            throw new BusinessException(
                    ProductErrorCode.OPTION_NOT_FOUND
            );
        }

        if (form == null) {
            throw new BusinessException(
                    ProductErrorCode.INVALID_OPTION
            );
        }

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
        ensureOptionGroupExists(productId, optionGroupId);
        Objects.requireNonNull(direction);

        List<ProductOptionGroupAdminView> groups =
                new ArrayList<>(
                        groupRows(
                                productMapper
                                        .findAdminOptionRowsByProductId(
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
        if (!productMapper.existsProductOptionById(
                productId,
                optionGroupId,
                optionId
        )) {
            throw new BusinessException(
                    ProductErrorCode.OPTION_NOT_FOUND
            );
        }
        Objects.requireNonNull(direction);

        List<ProductOptionAdminView> options =
                new ArrayList<>(
                        findOptionGroup(
                                productId,
                                optionGroupId
                        ).options()
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

    private ProductOptionGroupAdminView findOptionGroup(
            long productId,
            long optionGroupId
    ) {
        return groupRows(
                productMapper.findAdminOptionRowsByProductId(
                        productId
                )
        ).stream()
                .filter(group -> group.id() == optionGroupId)
                .findFirst()
                .orElseThrow(() ->
                        new BusinessException(
                                ProductErrorCode
                                        .OPTION_GROUP_NOT_FOUND
                        )
                );
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

    private void ensureProductExists(long productId) {
        if (productMapper.findAdminProductFormById(
                productId
        ) == null) {
            throw new BusinessException(
                    ProductErrorCode.NOT_FOUND
            );
        }
    }

    private void ensureOptionGroupExists(
            long productId,
            long optionGroupId
    ) {
        ensureProductExists(productId);

        if (!productMapper.existsOptionGroupById(
                productId,
                optionGroupId
        )) {
            throw new BusinessException(
                    ProductErrorCode.OPTION_GROUP_NOT_FOUND
            );
        }
    }
}
