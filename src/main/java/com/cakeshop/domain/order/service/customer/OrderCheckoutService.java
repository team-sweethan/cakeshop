package com.cakeshop.domain.order.service.customer;

import com.cakeshop.domain.order.dto.view.customer.GeneralOrderCheckoutView;
import com.cakeshop.domain.order.dto.view.customer.GeneralOrderCheckoutView.PickupDateView;
import com.cakeshop.domain.order.dto.view.customer.GeneralOrderCheckoutView.PickupTimeView;
import com.cakeshop.domain.order.dto.view.customer.GeneralOrderCheckoutView.SelectedOptionView;
import com.cakeshop.domain.order.error.OrderErrorCode;
import com.cakeshop.domain.order.service.OrderOptionValidator;
import com.cakeshop.domain.order.service.OrderOptionValidator.ValidatedOption;
import com.cakeshop.domain.product.dto.view.ProductSalesInfo;
import com.cakeshop.domain.product.entity.ProductType;
import com.cakeshop.domain.product.error.ProductErrorCode;
import com.cakeshop.domain.product.service.ProductQueryService;
import com.cakeshop.domain.store.dto.view.StoreView;
import com.cakeshop.domain.store.service.StoreService;
import com.cakeshop.global.error.BusinessException;
import com.cakeshop.global.error.CommonErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** 일반 상품 주문서에 필요한 서버 기준 조회 데이터를 구성한다. */
@Service
@RequiredArgsConstructor
public class OrderCheckoutService {

    private static final int PICKUP_WINDOW_DAYS = 14;
    private static final BigDecimal MAX_ORDER_AMOUNT = new BigDecimal("999999999999");
    private static final DateTimeFormatter DATE_LABEL_FORMATTER =
            DateTimeFormatter.ofPattern("M월 d일 (E)", Locale.KOREAN);
    private static final DateTimeFormatter TIME_LABEL_FORMATTER =
            DateTimeFormatter.ofPattern("HH:mm");

    private final ProductQueryService productQueryService;
    private final OrderOptionValidator orderOptionValidator;
    private final StoreService storeService;
    private final Clock clock;

    /** 클라이언트 가격을 사용하지 않고 현재 상품과 매장 정보로 주문서를 만든다. */
    @Transactional(readOnly = true)
    public GeneralOrderCheckoutView getGeneralCheckout(
            Long productId,
            Integer quantity,
            List<Long> optionIds
    ) {
        if (productId == null || productId <= 0) {
            throw new BusinessException(OrderErrorCode.EMPTY_ORDER_ITEMS);
        }
        if (quantity == null || quantity <= 0) {
            throw new BusinessException(OrderErrorCode.INVALID_QUANTITY);
        }

        // DB에 저장된 상품 정보 가져옴.
        ProductSalesInfo product = productQueryService.getSalesInfo(productId);
        // 주문 가능한 수량 검증
        validateProduct(product, quantity);
        // 주문 가능한 옵션 검증
        List<ValidatedOption> validatedOptions = orderOptionValidator.validate(
                productId,
                optionIds
        );

        BigDecimal productAmount = product.basePrice()
                .multiply(BigDecimal.valueOf(quantity));
        BigDecimal optionAmount = validatedOptions.stream()
                .map(ValidatedOption::additionalPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .multiply(BigDecimal.valueOf(quantity));
        BigDecimal totalAmount = productAmount.add(optionAmount);

        if (totalAmount.compareTo(MAX_ORDER_AMOUNT) > 0) {
            throw new BusinessException(OrderErrorCode.ORDER_AMOUNT_EXCEEDED);
        }

        List<SelectedOptionView> selectedOptions = validatedOptions.stream()
                .map(option -> new SelectedOptionView(
                        option.optionId(),
                        option.groupName(),
                        option.optionName(),
                        option.additionalPrice()
                ))
                .toList();

        return new GeneralOrderCheckoutView(
                product.productId(),
                product.productName(),
                product.productType().getDisplayName(),
                quantity,
                selectedOptions,
                productAmount,
                optionAmount,
                totalAmount,
                createPickupDates(storeService.getStoreView())
        );
    }

    private void validateProduct(ProductSalesInfo product, int quantity) {
        if (product.productType() != ProductType.GENERAL) {
            throw new BusinessException(OrderErrorCode.GENERAL_PRODUCT_REQUIRED);
        }
        if (product.basePrice() == null || product.basePrice().signum() < 0) {
            throw new BusinessException(CommonErrorCode.INTERNAL_ERROR);
        }
        if (!product.available()
                || product.stockQuantity() != null
                && product.stockQuantity() < quantity) {
            throw new BusinessException(ProductErrorCode.INSUFFICIENT_STOCK);
        }
    }

    private List<PickupDateView> createPickupDates(StoreView store) {
        LocalDateTime now = LocalDateTime.now(clock);
        List<PickupDateView> pickupDates = new ArrayList<>();

        for (int dayOffset = 0; dayOffset < PICKUP_WINDOW_DAYS; dayOffset++) {
            LocalDate date = now.toLocalDate().plusDays(dayOffset);
            List<PickupTimeView> times = createPickupTimes(date, now, store);
            if (!times.isEmpty()) {
                pickupDates.add(new PickupDateView(
                        date,
                        date.format(DATE_LABEL_FORMATTER),
                        times
                ));
            }
        }

        return List.copyOf(pickupDates);
    }

    private List<PickupTimeView> createPickupTimes(
            LocalDate date,
            LocalDateTime now,
            StoreView store
    ) {
        if (isClosed(date, store)
                || store.pickupStartTime() == null
                || store.pickupEndTime() == null
                || store.pickupIntervalMinutes() == null
                || store.pickupIntervalMinutes() <= 0) {
            return List.of();
        }

        DayOfWeek dayOfWeek = date.getDayOfWeek();
        boolean weekend = dayOfWeek == DayOfWeek.SATURDAY
                || dayOfWeek == DayOfWeek.SUNDAY;
        LocalTime businessStart = weekend
                ? store.weekendOpenTime()
                : store.weekdayOpenTime();
        LocalTime businessEnd = weekend
                ? store.weekendCloseTime()
                : store.weekdayCloseTime();
        if (businessStart == null || businessEnd == null) {
            return List.of();
        }

        LocalDateTime cursor = date.atTime(store.pickupStartTime());
        LocalDateTime lastPickup = date.atTime(store.pickupEndTime());
        List<PickupTimeView> times = new ArrayList<>();

        while (!cursor.isAfter(lastPickup)) {
            LocalTime time = cursor.toLocalTime();
            if (cursor.isAfter(now)
                    && !time.isBefore(businessStart)
                    && !time.isAfter(businessEnd)) {
                times.add(new PickupTimeView(
                        cursor,
                        time.format(TIME_LABEL_FORMATTER)
                ));
            }
            cursor = cursor.plusMinutes(store.pickupIntervalMinutes());
        }

        return List.copyOf(times);
    }

    private boolean isClosed(LocalDate date, StoreView store) {
        return store.closedDays().contains(date.getDayOfWeek())
                || store.holidays().stream()
                        .anyMatch(holiday -> holiday.getHolidayDate().equals(date));
    }
}
