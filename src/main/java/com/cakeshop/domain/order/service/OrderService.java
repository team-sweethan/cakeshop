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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** 일반 상품 주문 생성과 결제 전후 주문 상태 처리를 담당한다. */
@Service
@RequiredArgsConstructor
public class OrderService {

    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final long PAYMENT_EXPIRATION_MINUTES = 10L;

    private final ProductQueryService productQueryService;
    private final OrderOptionValidator orderOptionValidator;
    private final OrderMapper orderMapper;
    private final PaymentMapper paymentMapper;
    private final Clock clock;

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

        // 클라이언트가 전달한 가격을 사용하지 않고 현재 상품·옵션 정보로 금액을 다시 계산한다.
        PreparedOrderItem preparedItem = prepareItem(form);
        BigDecimal originalAmount = preparedItem.totalAmount();

        // 주문과 하위 스냅샷 중 하나라도 저장에 실패하면 전체 트랜잭션을 rollback한다.
        Order order = createOrder(memberId, form, originalAmount, now);
        requireOneRow(orderMapper.insertOrder(order), OrderErrorCode.ORDER_SAVE_FAILED);

        saveOrderItem(order.getId(), preparedItem);

        // Toss 승인 전 단계의 결제 시도를 주문과 같은 트랜잭션에서 READY로 생성한다.
        Payment payment = createReadyPayment(order);
        requireOneRow(
                paymentMapper.insertReadyPayment(payment),
                OrderErrorCode.PAYMENT_SAVE_FAILED
        );

        return order.getId();
    }

    /** 결제를 요청한 회원 소유의 결제 대기 일반 주문과 재고 차감 대상을 조회한다. */
    @Transactional(readOnly = true)
    public GeneralPaymentOrder getGeneralPaymentOrder(
            long memberId,
            long orderId
    ) {
        validateActiveMember(memberId);

        Order order = orderMapper.findOrderById(orderId)
                .orElseThrow(() ->
                        new BusinessException(CommonErrorCode.NOT_FOUND));

        if (!Long.valueOf(memberId).equals(order.getMemberId())) {
            // 주문 존재 여부를 다른 회원에게 노출하지 않는다.
            throw new BusinessException(CommonErrorCode.NOT_FOUND);
        }
        if (order.getOrderType() != OrderType.GENERAL
                || order.getStatus() != OrderStatus.PENDING_PAYMENT) {
            throw new BusinessException(
                    OrderErrorCode.INVALID_STATUS_TRANSITION
            );
        }

        List<PaymentProduct> products = orderMapper
                .findOrderItemsByOrderId(orderId)
                .stream()
                .map(this::toPaymentProduct)
                .toList();
        if (products.isEmpty()) {
            throw new BusinessException(OrderErrorCode.EMPTY_ORDER_ITEMS);
        }

        return new GeneralPaymentOrder(
                order.getId(),
                order.getFinalAmount(),
                order.getPaymentExpiresAt(),
                products
        );
    }

    /** DONE 결제가 저장된 일반 주문을 픽업 대기 상태로 변경한다. */
    @Transactional
    public void completeGeneralOrderAfterPayment(
            long orderId,
            LocalDateTime readyAt
    ) {
        requireOneRow(
                orderMapper.markReadyForPickupAfterPaymentIfPending(
                        orderId,
                        readyAt
                ),
                OrderErrorCode.INVALID_STATUS_TRANSITION
        );
    }

    /** 현재는 유효한 회원 식별자 형식만 확인한다. */
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

    /** 현재 상품·옵션 정보를 검증하고 주문 항목 스냅샷에 저장할 값을 계산한다. */
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

    /**
     * 주문 생성 시점의 재고만 사전 확인한다.
     *
     * <p>실제 재고 차감은 결제 성공 처리에서 원자적으로 수행해야 한다.</p>
     */
    private void validateStock(ProductSalesInfo product, int quantity) {
        Integer stockQuantity = product.stockQuantity();
        if (!product.available()
                || stockQuantity != null && stockQuantity < quantity) {
            throw new BusinessException(ProductErrorCode.INSUFFICIENT_STOCK);
        }
    }

    private PaymentProduct toPaymentProduct(OrderItem orderItem) {
        if (orderItem.getProductType() != ProductType.GENERAL
                || orderItem.getProductId() == null
                || orderItem.getQuantity() == null
                || orderItem.getQuantity() <= 0) {
            throw new BusinessException(CommonErrorCode.INTERNAL_ERROR);
        }

        return new PaymentProduct(
                orderItem.getProductId(),
                orderItem.getQuantity()
        );
    }

    /** 결제 대기 상태와 결제 만료 시각이 설정된 일반 주문을 구성한다. */
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

    /** 주문 당시 상품과 선택 옵션 정보를 변경되지 않는 스냅샷으로 저장한다. */
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

    /** 주문의 최종 결제 금액을 기준으로 Toss 승인 전 READY 결제 정보를 구성한다. */
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

    /** 결제 검증과 완료 처리에 필요한 일반 주문 정보다. */
    public record GeneralPaymentOrder(
            long orderId,
            BigDecimal amount,
            LocalDateTime paymentExpiresAt,
            List<PaymentProduct> products
    ) {
    }

    /** 결제 성공 시 재고를 차감할 주문 상품이다. */
    public record PaymentProduct(long productId, int quantity) {
    }

    /** 검증된 상품 정보와 서버에서 계산한 주문 항목 금액을 전달한다. */
    private record PreparedOrderItem(
            ProductSalesInfo product,
            int quantity,
            List<ValidatedOption> selectedOptions,
            BigDecimal optionAmount,
            BigDecimal totalAmount
    ) {
    }
}
