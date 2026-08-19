package com.cakeshop.domain.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;

import com.cakeshop.domain.order.error.OrderErrorCode;
import com.cakeshop.domain.order.service.checkout.OrderOptionValidator;
import com.cakeshop.domain.order.service.checkout.OrderOptionValidator.ValidatedOption;
import com.cakeshop.domain.product.dto.view.ProductOptionGroupView;
import com.cakeshop.domain.product.dto.view.ProductOptionItemView;
import com.cakeshop.domain.product.service.ProductService;
import com.cakeshop.global.error.BusinessException;
import com.cakeshop.global.error.CommonErrorCode;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OrderOptionValidatorTests {

    @Mock
    private ProductService productService;

    private OrderOptionValidator validator;

    @BeforeEach
    void setUp() {
        validator = new OrderOptionValidator(productService);
    }

    @Test
    void validate_validOptions_returnsSnapshotsInRequestOrder() {
        when(productService.getPublicOptionGroups(1L))
                .thenReturn(List.of(
                        optionGroup(
                                "크기",
                                true,
                                "SINGLE",
                                option(101L, "1호", 0),
                                option(102L, "2호", 5_000)
                        ),
                        optionGroup(
                                "추가",
                                false,
                                "MULTIPLE",
                                option(201L, "초 추가", 2_000)
                        )
                ));

        List<ValidatedOption> result =
                validator.validate(1L, List.of(201L, 102L));

        assertThat(result)
                .extracting(ValidatedOption::optionId)
                .containsExactly(201L, 102L);
        assertThat(result.getFirst()).satisfies(option -> {
            assertThat(option.groupName()).isEqualTo("추가");
            assertThat(option.optionName()).isEqualTo("초 추가");
            assertThat(option.additionalPrice())
                    .isEqualByComparingTo("2000");
        });
    }

    @Test
    void validate_duplicateOption_throwsInvalidProductOption() {
        assertInvalidOption(
                () -> validator.validate(1L, List.of(101L, 101L))
        );

        verify(productService, never()).getPublicOptionGroups(1L);
    }

    @Test
    void validate_unknownOption_throwsInvalidProductOption() {
        when(productService.getPublicOptionGroups(1L))
                .thenReturn(List.of(optionGroup(
                        "크기",
                        false,
                        "SINGLE",
                        option(101L, "1호", 0)
                )));

        assertInvalidOption(
                () -> validator.validate(1L, List.of(999L))
        );
    }

    @Test
    void validate_requiredOptionMissing_throwsInvalidProductOption() {
        when(productService.getPublicOptionGroups(1L))
                .thenReturn(List.of(optionGroup(
                        "크기",
                        true,
                        "SINGLE",
                        option(101L, "1호", 0)
                )));

        assertInvalidOption(
                () -> validator.validate(1L, List.of())
        );
    }

    @Test
    void validate_singleGroupMultipleSelection_throwsInvalidProductOption() {
        when(productService.getPublicOptionGroups(1L))
                .thenReturn(List.of(optionGroup(
                        "크기",
                        false,
                        "SINGLE",
                        option(101L, "1호", 0),
                        option(102L, "2호", 5_000)
                )));

        assertInvalidOption(
                () -> validator.validate(1L, List.of(101L, 102L))
        );
    }

    @Test
    void validate_negativeOptionPrice_throwsInternalError() {
        when(productService.getPublicOptionGroups(1L))
                .thenReturn(List.of(optionGroup(
                        "크기",
                        false,
                        "SINGLE",
                        option(101L, "1호", -1)
                )));

        assertThatThrownBy(() ->
                validator.validate(1L, List.of(101L))
        ).isInstanceOfSatisfying(
                BusinessException.class,
                error -> assertThat(error.getErrorCode())
                        .isEqualTo(CommonErrorCode.INTERNAL_ERROR)
        );
    }

    @Test
    void validate_unknownSelectionType_throwsInternalError() {
        when(productService.getPublicOptionGroups(1L))
                .thenReturn(List.of(optionGroup(
                        "크기",
                        false,
                        "UNKNOWN",
                        option(101L, "1호", 0)
                )));

        assertThatThrownBy(() ->
                validator.validate(1L, List.of(101L))
        ).isInstanceOfSatisfying(
                BusinessException.class,
                error -> assertThat(error.getErrorCode())
                        .isEqualTo(CommonErrorCode.INTERNAL_ERROR)
        );
    }

    private ProductOptionGroupView optionGroup(
            String name,
            boolean required,
            String selectionType,
            ProductOptionItemView... options
    ) {
        return new ProductOptionGroupView(
                (long) name.hashCode(),
                name,
                required,
                selectionType,
                List.of(options)
        );
    }

    private ProductOptionItemView option(
            long id,
            String name,
            long additionalPrice
    ) {
        return new ProductOptionItemView(
                id,
                name,
                BigDecimal.valueOf(additionalPrice)
        );
    }

    private void assertInvalidOption(ThrowingCall call) {
        assertThatThrownBy(call::run)
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        error -> assertThat(error.getErrorCode())
                                .isEqualTo(
                                        OrderErrorCode.INVALID_PRODUCT_OPTION
                                )
                );
    }

    @FunctionalInterface
    private interface ThrowingCall {
        void run();
    }
}
