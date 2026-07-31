package com.cakeshop.domain.order.service;

import com.cakeshop.domain.order.dto.form.GeneralOrderForm;
import com.cakeshop.domain.order.entity.Order;
import com.cakeshop.domain.order.entity.OrderItem;
import com.cakeshop.domain.order.entity.OrderItemOption;
import com.cakeshop.domain.order.entity.OrderStatus;
import com.cakeshop.domain.order.entity.OrderType;
import com.cakeshop.domain.order.error.OrderErrorCode;
import com.cakeshop.domain.order.mapper.OrderMapper;
import com.cakeshop.domain.order.service.OrderOptionValidator.ValidatedOption;
import com.cakeshop.domain.payment.entity.Payment;
import com.cakeshop.domain.payment.entity.PaymentStatus;
import com.cakeshop.domain.payment.mapper.PaymentMapper;
import com.cakeshop.domain.product.dto.view.ProductSalesInfo;
import com.cakeshop.domain.product.entity.ProductType;
import com.cakeshop.domain.product.error.ProductErrorCode;
import com.cakeshop.domain.product.service.ProductQueryService;
import com.cakeshop.global.error.BusinessException;
import com.cakeshop.global.error.CommonErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class OrderService {

    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final long PAYMENT_EXPIRATION_MINUTES = 10L;

    private final ProductQueryService productQueryService;
    private final OrderOptionValidator orderOptionValidator;
    private final OrderMapper orderMapper;
    private final PaymentMapper paymentMapper;
    private final Clock clock;

    public OrderService(
            ProductQueryService productQueryService,
            OrderOptionValidator orderOptionValidator,
            OrderMapper orderMapper,
            PaymentMapper paymentMapper,
            Clock clock
    ) {
        this.productQueryService = productQueryService;
        this.orderOptionValidator = orderOptionValidator;
        this.orderMapper = orderMapper;
        this.paymentMapper = paymentMapper;
        this.clock = clock;
    }

    /**
     * 일반 상품 주문, 주문 항목 스냅샷, READY 결제를 하나의 트랜잭션으로 생성한다.
     *
     * @return 결제 화면으로 이동할 때 사용할 주문 ID
     */
    @Transactional
    public long createGeneralOrder(long memberId, GeneralOrderForm form) {
        validateActiveMember(memberId);
        validateForm(form);

        LocalDateTime now = LocalDateTime.now(clock);
        validatePickupAt(form.getPickupAt(), now);
        PreparedOrderItem preparedItem = prepareItem(form);
        BigDecimal originalAmount = preparedItem.totalAmount();

        Order order = createOrder(memberId, form, originalAmount, now);
        requireOneRow(orderMapper.insertOrder(order), OrderErrorCode.ORDER_SAVE_FAILED);

        saveOrderItem(order.getId(), preparedItem);

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

    private void validatePickupAt(
            LocalDateTime pickupAt,
            LocalDateTime now
    ) {
        if (pickupAt == null || !pickupAt.isAfter(now)) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }
    }

    private void validateForm(GeneralOrderForm form) {
        if (form == null
                || isBlank(form.getOrdererName())
                || isBlank(form.getOrdererPhone())
                || isBlank(form.getPickupName())
                || isBlank(form.getPickupPhone())) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }
        if (form.getProductId() == null) {
            throw new BusinessException(OrderErrorCode.EMPTY_ORDER_ITEMS);
        }
    }

    private PreparedOrderItem prepareItem(GeneralOrderForm form) {
        if (form.getQuantity() == null || form.getQuantity() <= 0) {
            throw new BusinessException(OrderErrorCode.INVALID_QUANTITY);
        }

        ProductSalesInfo product =
                productQueryService.getSalesInfo(form.getProductId());
        if (product.productType() != ProductType.GENERAL) {
            throw new BusinessException(OrderErrorCode.GENERAL_PRODUCT_REQUIRED);
        }
        if (product.basePrice() == null || product.basePrice().signum() < 0) {
            throw new BusinessException(CommonErrorCode.INTERNAL_ERROR);
        }
        validateStock(product, form.getQuantity());

        List<ValidatedOption> selectedOptions =
                orderOptionValidator.validate(
                        product.productId(),
                        form.getOptionIds()
                );
        BigDecimal optionAmount = selectedOptions.stream()
                .map(ValidatedOption::additionalPrice)
                .reduce(ZERO, BigDecimal::add);
        BigDecimal totalAmount = product.basePrice()
                .add(optionAmount)
                .multiply(BigDecimal.valueOf(form.getQuantity()));

        return new PreparedOrderItem(
                product,
                form.getQuantity(),
                selectedOptions,
                optionAmount,
                totalAmount
        );
    }

    private void validateStock(ProductSalesInfo product, int quantity) {
        Integer stockQuantity = product.stockQuantity();
        if (!product.available()
                || stockQuantity != null && stockQuantity < quantity) {
            throw new BusinessException(ProductErrorCode.INSUFFICIENT_STOCK);
        }
    }

    private Order createOrder(
            long memberId,
            GeneralOrderForm form,
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
        ProductSalesInfo product = preparedItem.product();
        OrderItem orderItem = new OrderItem();
        orderItem.setOrderId(orderId);
        orderItem.setProductId(product.productId());
        orderItem.setProductName(product.productName());
        orderItem.setProductType(product.productType());
        orderItem.setQuantity(preparedItem.quantity());
        orderItem.setBasePrice(product.basePrice());
        orderItem.setOptionAmount(preparedItem.optionAmount());
        orderItem.setTotalAmount(preparedItem.totalAmount());
        orderItem.setPreparationDays(product.preparationDays());
        orderItem.setCancellationLimitDays(0);
        requireOneRow(
                orderMapper.insertOrderItem(orderItem),
                OrderErrorCode.ORDER_SAVE_FAILED
        );

        for (ValidatedOption selectedOption : preparedItem.selectedOptions()) {
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
            ProductSalesInfo product,
            int quantity,
            List<ValidatedOption> selectedOptions,
            BigDecimal optionAmount,
            BigDecimal totalAmount
    ) {
    }
}
