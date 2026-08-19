package com.cakeshop.domain.order.service.checkout;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.cakeshop.domain.order.error.OrderErrorCode;
import com.cakeshop.domain.product.dto.view.ProductOptionGroupView;
import com.cakeshop.domain.product.dto.view.ProductOptionItemView;
import com.cakeshop.domain.product.service.ProductService;
import com.cakeshop.global.error.BusinessException;
import com.cakeshop.global.error.CommonErrorCode;

import org.springframework.stereotype.Component;

/** 상품 옵션 선택을 검증하고 주문 스냅샷에 저장할 값을 반환한다. */
@Component
public class OrderOptionValidator {

    private static final String SINGLE = "SINGLE";
    private static final String MULTIPLE = "MULTIPLE";

    private final ProductService productService;

    public OrderOptionValidator(ProductService productService) {
        this.productService = productService;
    }

    public List<ValidatedOption> validate(
            long productId,
            List<Long> requestedOptionIds
    ) {
        List<Long> optionIds =
                requestedOptionIds == null ? List.of() : requestedOptionIds;
        Set<Long> uniqueOptionIds = validateRequestedOptionIds(optionIds);
        List<ProductOptionGroupView> optionGroups =
                productService.getPublicOptionGroups(productId);
        Map<Long, ValidatedOption> optionsById =
                collectAvailableOptions(optionGroups, uniqueOptionIds);

        if (!optionsById.keySet().containsAll(uniqueOptionIds)) {
            throw new BusinessException(OrderErrorCode.INVALID_PRODUCT_OPTION);
        }

        return optionIds.stream()
                .map(optionsById::get)
                .toList();
    }

    private Set<Long> validateRequestedOptionIds(List<Long> optionIds) {
        Set<Long> uniqueOptionIds = new HashSet<>();

        for (Long optionId : optionIds) {
            if (optionId == null
                    || optionId <= 0
                    || !uniqueOptionIds.add(optionId)) {
                throw new BusinessException(
                        OrderErrorCode.INVALID_PRODUCT_OPTION
                );
            }
        }

        return uniqueOptionIds;
    }

    private Map<Long, ValidatedOption> collectAvailableOptions(
            List<ProductOptionGroupView> optionGroups,
            Set<Long> requestedOptionIds
    ) {
        Map<Long, ValidatedOption> optionsById = new HashMap<>();

        for (ProductOptionGroupView group : optionGroups) {
            validateSelectionType(group.selectionType());

            long selectedCount = group.options().stream()
                    .filter(option -> requestedOptionIds.contains(option.id()))
                    .count();
            validateGroupSelection(group, selectedCount);

            for (ProductOptionItemView option : group.options()) {
                validateOption(option);
                ValidatedOption previous = optionsById.put(
                        option.id(),
                        new ValidatedOption(
                                option.id(),
                                group.name(),
                                option.name(),
                                option.additionalPrice()
                        )
                );
                if (previous != null) {
                    throw new BusinessException(
                            CommonErrorCode.INTERNAL_ERROR
                    );
                }
            }
        }

        return optionsById;
    }

    private void validateSelectionType(String selectionType) {
        if (!SINGLE.equals(selectionType)
                && !MULTIPLE.equals(selectionType)) {
            throw new BusinessException(CommonErrorCode.INTERNAL_ERROR);
        }
    }

    private void validateGroupSelection(
            ProductOptionGroupView group,
            long selectedCount
    ) {
        if (group.required() && selectedCount == 0) {
            throw new BusinessException(
                    OrderErrorCode.INVALID_PRODUCT_OPTION
            );
        }
        if (SINGLE.equals(group.selectionType()) && selectedCount > 1) {
            throw new BusinessException(
                    OrderErrorCode.INVALID_PRODUCT_OPTION
            );
        }
    }

    private void validateOption(ProductOptionItemView option) {
        if (option.additionalPrice() == null
                || option.additionalPrice().signum() < 0) {
            throw new BusinessException(CommonErrorCode.INTERNAL_ERROR);
        }
    }

    public record ValidatedOption(
            long optionId,
            String groupName,
            String optionName,
            BigDecimal additionalPrice
    ) {
    }
}
