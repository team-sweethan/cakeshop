package com.cakeshop.domain.order.service;

import com.cakeshop.domain.order.dto.form.CreateOrderForm;
import com.cakeshop.domain.order.dto.form.OrderItemForm;
import com.cakeshop.domain.order.entity.Order;
import com.cakeshop.domain.order.entity.OrderItem;
import com.cakeshop.domain.order.entity.OrderItemOption;
import com.cakeshop.domain.order.entity.OrderStatus;
import com.cakeshop.domain.order.entity.OrderType;
import com.cakeshop.domain.order.error.OrderErrorCode;
import com.cakeshop.domain.order.mapper.OrderMapper;
import com.cakeshop.domain.payment.entity.Payment;
import com.cakeshop.domain.payment.entity.PaymentStatus;
import com.cakeshop.domain.payment.mapper.PaymentMapper;
import com.cakeshop.domain.product.customer.dto.view.ProductDetailView;
import com.cakeshop.domain.product.customer.dto.view.ProductOptionRow;
import com.cakeshop.domain.product.entity.ProductType;
import com.cakeshop.domain.product.error.ProductErrorCode;
import com.cakeshop.domain.product.mapper.ProductMapper;
import com.cakeshop.global.error.BusinessException;
import com.cakeshop.global.error.CommonErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class OrderService {

    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final long PAYMENT_EXPIRATION_MINUTES = 10L;

    private final ProductMapper productMapper;
    private final OrderMapper orderMapper;
    private final PaymentMapper paymentMapper;

    public OrderService(
            ProductMapper productMapper,
            OrderMapper orderMapper,
            PaymentMapper paymentMapper
    ) {
        this.productMapper = productMapper;
        this.orderMapper = orderMapper;
        this.paymentMapper = paymentMapper;
    }

    /**
     * 일반 상품 주문, 주문 항목 스냅샷, READY 결제를 하나의 트랜잭션으로 생성한다.
     *
     * @return 결제 화면으로 이동할 때 사용할 주문 ID
     */
    @Transactional
    public long createGeneralOrder(long memberId, CreateOrderForm form) {
        validateActiveMember(memberId);
        validateForm(form);

        List<PreparedOrderItem> preparedItems = prepareItems(form.getItems());
        BigDecimal originalAmount = preparedItems.stream()
                .map(PreparedOrderItem::totalAmount)
                .reduce(ZERO, BigDecimal::add);
        LocalDateTime now = LocalDateTime.now();

        if (form.getPickupAt() == null || !form.getPickupAt().isAfter(now)) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }

        Order order = createOrder(memberId, form, originalAmount, now);
        requireOneRow(orderMapper.insertOrder(order), OrderErrorCode.ORDER_SAVE_FAILED);

        for (PreparedOrderItem preparedItem : preparedItems) {
            saveOrderItem(order.getId(), preparedItem);
        }

        Payment payment = createReadyPayment(order);
        requireOneRow(
                paymentMapper.insertReadyPayment(payment),
                OrderErrorCode.PAYMENT_SAVE_FAILED
        );

        return order.getId();
    }

    private void validateActiveMember(long memberId) {
        if (memberId <= 0) {
            throw new BusinessException(OrderErrorCode.MEMBER_NOT_AVAILABLE);
        }
    }

    private void validateForm(CreateOrderForm form) {
        if (form == null
                || isBlank(form.getOrdererName())
                || isBlank(form.getOrdererPhone())
                || isBlank(form.getPickupName())
                || isBlank(form.getPickupPhone())) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }
        if (form.getItems() == null || form.getItems().isEmpty()) {
            throw new BusinessException(OrderErrorCode.EMPTY_ORDER_ITEMS);
        }
    }

    private List<PreparedOrderItem> prepareItems(List<OrderItemForm> itemForms) {
        List<PreparedOrderItem> result = new ArrayList<>();

        for (OrderItemForm itemForm : itemForms) {
            if (itemForm == null || itemForm.getProductId() == null) {
                throw new BusinessException(CommonErrorCode.INVALID_INPUT);
            }
            if (itemForm.getQuantity() == null || itemForm.getQuantity() <= 0) {
                throw new BusinessException(OrderErrorCode.INVALID_QUANTITY);
            }

            ProductDetailView product = productMapper.findPublicDetailById(itemForm.getProductId());
            if (product == null) {
                throw new BusinessException(ProductErrorCode.NOT_FOUND);
            }
            if (product.getProductType() != ProductType.GENERAL) {
                throw new BusinessException(OrderErrorCode.GENERAL_PRODUCT_REQUIRED);
            }
            if (product.getBasePrice() == null || product.getBasePrice().signum() < 0) {
                throw new BusinessException(CommonErrorCode.INTERNAL_ERROR);
            }

            List<ProductOptionRow> selectedOptions =
                    resolveSelectedOptions(product.getId(), itemForm.getOptionIds());
            BigDecimal optionAmount = selectedOptions.stream()
                    .map(ProductOptionRow::additionalPrice)
                    .reduce(ZERO, BigDecimal::add);
            BigDecimal totalAmount = product.getBasePrice()
                    .add(optionAmount)
                    .multiply(BigDecimal.valueOf(itemForm.getQuantity()));

            result.add(new PreparedOrderItem(
                    product,
                    itemForm.getQuantity(),
                    selectedOptions,
                    optionAmount,
                    totalAmount
            ));
        }

        return result;
    }

    private List<ProductOptionRow> resolveSelectedOptions(
            long productId,
            List<Long> requestedOptionIds
    ) {
        List<ProductOptionRow> availableOptions =
                productMapper.findPublicOptionRowsByProductId(productId);
        List<Long> optionIds =
                requestedOptionIds == null ? List.of() : requestedOptionIds;
        Set<Long> uniqueOptionIds = new HashSet<>();

        for (Long optionId : optionIds) {
            if (optionId == null || optionId <= 0 || !uniqueOptionIds.add(optionId)) {
                throw new BusinessException(OrderErrorCode.INVALID_PRODUCT_OPTION);
            }
        }

        Map<Long, ProductOptionRow> optionsById = new HashMap<>();
        for (ProductOptionRow option : availableOptions) {
            if (option.additionalPrice() == null
                    || option.additionalPrice().signum() < 0) {
                throw new BusinessException(CommonErrorCode.INTERNAL_ERROR);
            }
            optionsById.put(option.optionId(), option);
        }

        if (!optionsById.keySet().containsAll(uniqueOptionIds)) {
            throw new BusinessException(OrderErrorCode.INVALID_PRODUCT_OPTION);
        }

        List<ProductOptionRow> selectedOptions = availableOptions.stream()
                .filter(option -> uniqueOptionIds.contains(option.optionId()))
                .toList();
        validateOptionGroups(availableOptions, selectedOptions);
        return selectedOptions;
    }

    private void validateOptionGroups(
            List<ProductOptionRow> availableOptions,
            List<ProductOptionRow> selectedOptions
    ) {
        Map<Long, Long> selectedCountByGroup = selectedOptions.stream()
                .collect(Collectors.groupingBy(
                        ProductOptionRow::groupId,
                        Collectors.counting()
                ));

        for (ProductOptionRow option : availableOptions) {
            long selectedCount =
                    selectedCountByGroup.getOrDefault(option.groupId(), 0L);
            if (option.required() && selectedCount == 0) {
                throw new BusinessException(OrderErrorCode.INVALID_PRODUCT_OPTION);
            }
            if ("SINGLE".equals(option.selectionType()) && selectedCount > 1) {
                throw new BusinessException(OrderErrorCode.INVALID_PRODUCT_OPTION);
            }
        }
    }

    private Order createOrder(
            long memberId,
            CreateOrderForm form,
            BigDecimal originalAmount,
            LocalDateTime now
    ) {
        Order order = new Order();
        order.setOrderNumber("ORD-" + compactUuid());
        order.setMemberId(memberId);
        order.setOrderType(OrderType.GENERAL);
        order.setOrdererName(form.getOrdererName().trim());
        order.setOrdererPhone(form.getOrdererPhone().trim());
        order.setPickupName(form.getPickupName().trim());
        order.setPickupPhone(form.getPickupPhone().trim());
        order.setOriginalAmount(originalAmount);
        order.setDiscountAmount(ZERO);
        order.setFinalAmount(originalAmount);
        order.setStatus(OrderStatus.PENDING_PAYMENT);
        order.setPickupAt(form.getPickupAt());
        order.setPaymentExpiresAt(now.plusMinutes(PAYMENT_EXPIRATION_MINUTES));
        order.setRequestMessage(trimToNull(form.getRequestMessage()));
        return order;
    }

    private void saveOrderItem(long orderId, PreparedOrderItem preparedItem) {
        ProductDetailView product = preparedItem.product();
        OrderItem orderItem = new OrderItem();
        orderItem.setOrderId(orderId);
        orderItem.setProductId(product.getId());
        orderItem.setProductName(product.getName());
        orderItem.setProductType(product.getProductType());
        orderItem.setQuantity(preparedItem.quantity());
        orderItem.setBasePrice(product.getBasePrice());
        orderItem.setOptionAmount(preparedItem.optionAmount());
        orderItem.setTotalAmount(preparedItem.totalAmount());
        orderItem.setPreparationDays(product.getPreparationDays());
        orderItem.setCancellationLimitDays(0);
        requireOneRow(
                orderMapper.insertOrderItem(orderItem),
                OrderErrorCode.ORDER_SAVE_FAILED
        );

        for (ProductOptionRow selectedOption : preparedItem.selectedOptions()) {
            OrderItemOption snapshot = new OrderItemOption();
            snapshot.setOrderItemId(orderItem.getId());
            snapshot.setProductOptionId(selectedOption.optionId());
            snapshot.setOptionGroupName(selectedOption.groupName());
            snapshot.setOptionName(selectedOption.optionName());
            snapshot.setAdditionalPrice(selectedOption.additionalPrice());
            requireOneRow(
                    orderMapper.insertOrderItemOption(snapshot),
                    OrderErrorCode.ORDER_SAVE_FAILED
            );
        }
    }

    private Payment createReadyPayment(Order order) {
        Payment payment = new Payment();
        payment.setOrderId(order.getId());
        payment.setTossOrderId(order.getOrderNumber());
        payment.setIdempotencyKey("PAY-" + compactUuid());
        payment.setAmount(order.getFinalAmount());
        payment.setStatus(PaymentStatus.READY);
        return payment;
    }

    private void requireOneRow(int affectedRows, OrderErrorCode errorCode) {
        if (affectedRows != 1) {
            throw new BusinessException(errorCode);
        }
    }

    private String compactUuid() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String trimToNull(String value) {
        return isBlank(value) ? null : value.trim();
    }

    private record PreparedOrderItem(
            ProductDetailView product,
            int quantity,
            List<ProductOptionRow> selectedOptions,
            BigDecimal optionAmount,
            BigDecimal totalAmount
    ) {
    }
}
