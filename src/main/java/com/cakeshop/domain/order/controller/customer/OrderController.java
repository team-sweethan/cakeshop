package com.cakeshop.domain.order.controller.customer;

import com.cakeshop.domain.member.dto.view.MemberProfileView;
import com.cakeshop.domain.member.service.MemberService;
import com.cakeshop.domain.coupon.service.CouponOrderQueryService;
import com.cakeshop.domain.order.dto.form.OrderCancelForm;
import com.cakeshop.domain.order.dto.form.customer.OrderCartCreateForm;
import com.cakeshop.domain.order.dto.form.customer.OrderCustomCreateForm;
import com.cakeshop.domain.order.dto.form.customer.OrderGeneralCreateForm;
import com.cakeshop.domain.order.dto.view.customer.OrderCreationResult;
import com.cakeshop.domain.cart.service.CartOrderQueryService;
import com.cakeshop.domain.order.error.OrderErrorCode;
import com.cakeshop.domain.order.service.customer.OrderCheckoutService;
import com.cakeshop.domain.order.service.customer.CustomerCustomOrderService;
import com.cakeshop.domain.product.service.ProductQueryService;
import com.cakeshop.domain.order.service.customer.OrderCustomerService;
import com.cakeshop.domain.order.service.OrderService;
import com.cakeshop.domain.payment.service.RefundFacade;
import com.cakeshop.global.error.BusinessException;
import com.cakeshop.global.error.CommonErrorCode;
import com.cakeshop.global.security.MemberDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;
import java.util.UUID;

@Controller
@RequestMapping("/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderCheckoutService orderCheckoutService;
    private final OrderService orderService;
    private final OrderCustomerService orderCustomerService;
    private final MemberService memberService;
    private final RefundFacade refundFacade;
    private final CouponOrderQueryService couponOrderQueryService;
    private final CustomerCustomOrderService customerCustomOrderService;
    private final ProductQueryService productQueryService;
    private final CartOrderQueryService cartOrderQueryService;

    /** 장바구니 항목의 픽업 일시 수정용 목업 경로다. 일반 주문은 checkout에서 선택한다. */
    @GetMapping(value = "/pickup", params = "intent=cart-edit")
    public String pickupSetting() {
        return "customer/order/pickup-setting";
    }

    @GetMapping("/custom/options")
    public String customOptions(
            @RequestParam("productId") Long productId,
            Model model
    ) {
        model.addAttribute("customProduct", productQueryService.getSalesInfo(productId));
        model.addAttribute("optionGroups", orderCheckoutService.getCustomOptionGroups(productId));
        return "customer/order/custom-option";
    }

    @GetMapping("/custom/request")
    public String customRequest(
            @AuthenticationPrincipal MemberDetails member,
            @ModelAttribute("orderForm") OrderCustomCreateForm form,
            Model model
    ) {
        prefillMemberContact(form, member);
        form.setRequestKey(UUID.randomUUID().toString());
        return renderCustomOrderForm(form, model, requireMemberId(member));
    }

    @PostMapping("/custom")
    public String createCustomOrder(
            @AuthenticationPrincipal MemberDetails member,
            @Valid @ModelAttribute("orderForm") OrderCustomCreateForm form,
            BindingResult bindingResult,
            Model model
    ) {
        long memberId = requireMemberId(member);
        if (bindingResult.hasErrors()) {
            return renderCustomOrderForm(form, model, memberId);
        }
        try {
            OrderCreationResult result = customerCustomOrderService.createCustomOrder(memberId, form);
            return paymentRedirectOrPendingGuide(
                    result,
                    model,
                    customOrderFormUrl(form)
            );
        } catch (BusinessException exception) {
            if (exception.getErrorCode() != OrderErrorCode.ORDER_AMOUNT_CHANGED) {
                throw exception;
            }
            bindingResult.reject("orderAmountChanged", exception.getErrorCode().message());
            return renderCustomOrderForm(form, model, memberId);
        }
    }

    // 일반 상품 주문서 화면
    @GetMapping("/checkout")
    public String checkout(
            @ModelAttribute("orderForm") OrderGeneralCreateForm form,
            @AuthenticationPrincipal MemberDetails member,
            Model model
    ) {
        prefillMemberContact(form, member);
        form.setRequestKey(UUID.randomUUID().toString());

        return renderGeneralOrderForm(form, model, requireMemberId(member));
    }

    /** 장바구니에서 선택한 여러 일반 상품의 주문서다. 선택 ID는 cart 공개 조회 계약으로 소유권을 확인한다. */
    @GetMapping("/checkout/cart")
    public String cartCheckout(
            @RequestParam(value = "itemIds", required = false) List<Long> itemIds,
            @AuthenticationPrincipal MemberDetails member,
            Model model,
            RedirectAttributes redirectAttributes
    ) {
        if (itemIds == null || itemIds.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage", "주문할 상품을 선택해 주세요.");
            return "redirect:/cart";
        }
        long memberId = requireMemberId(member);
        OrderCartCreateForm form = new OrderCartCreateForm();
        form.setCartItemIds(itemIds);
        form.setRequestKey(UUID.randomUUID().toString());
        prefillMemberContact(form, member);
        return renderCartOrderForm(form, model, memberId);
    }

    // 일반 상품 주문 생성
    @PostMapping("/general")
    public String createGeneralOrder(
            @AuthenticationPrincipal MemberDetails member,
            @Valid @ModelAttribute("orderForm") OrderGeneralCreateForm form,
            BindingResult bindingResult,
            Model model
    ) {
        if (bindingResult.hasErrors()) {
            if (hasInvalidOrderItem(bindingResult)) {
                throw new BusinessException(CommonErrorCode.INVALID_INPUT);
            }
            return renderGeneralOrderForm(form, model, requireMemberId(member));
        }
        long memberId = requireMemberId(member);

        try {
            OrderCreationResult result = orderService.createGeneralOrder(memberId, form);
            return paymentRedirectOrPendingGuide(
                    result,
                    model,
                    generalOrderFormUrl(form)
            );
        } catch (BusinessException exception) {
            if (exception.getErrorCode() != OrderErrorCode.ORDER_AMOUNT_CHANGED) {
                throw exception;
            }
            bindingResult.reject("orderAmountChanged", exception.getErrorCode().message());
            return renderGeneralOrderForm(form, model, memberId);
        }
    }

    /** 장바구니 다건 일반 주문을 생성하고 결제 화면으로 이동한다. */
    @PostMapping("/general/cart")
    public String createCartOrder(
            @AuthenticationPrincipal MemberDetails member,
            @Valid @ModelAttribute("orderForm") OrderCartCreateForm form,
            BindingResult bindingResult,
            Model model
    ) {
        long memberId = requireMemberId(member);
        if (bindingResult.hasErrors()) {
            return renderCartOrderForm(form, model, memberId);
        }
        try {
            OrderCreationResult result = orderService.createCartOrder(memberId, form);
            return paymentRedirectOrPendingGuide(
                    result,
                    model,
                    cartOrderFormUrl(form)
            );
        } catch (BusinessException exception) {
            if (exception.getErrorCode() != OrderErrorCode.ORDER_AMOUNT_CHANGED) {
                throw exception;
            }
            bindingResult.reject("orderAmountChanged", exception.getErrorCode().message());
            return renderCartOrderForm(form, model, memberId);
        }
    }

    // 로그인 회원의 주문 취소
    @PostMapping("/{orderId}/cancel")
    public String cancel(
            @PathVariable("orderId") long orderId,
            @AuthenticationPrincipal MemberDetails member,
            @Valid @ModelAttribute OrderCancelForm form,
            BindingResult bindingResult
    ) {
        if (bindingResult.hasErrors()) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }
        refundFacade.cancelCustomerOrder(requireMemberId(member), orderId, form.getReason());
        return "redirect:/orders/" + orderId;
    }

    // 로그인 회원의 주문 목록
    @GetMapping
    public String orders(
            @AuthenticationPrincipal MemberDetails member,
            Model model
    ) {
        long memberId = requireMemberId(member);
        model.addAttribute(
                "orders",
                orderCustomerService.getMemberOrders(memberId)
        );
        return "customer/order/detail";
    }

    // 로그인 회원의 주문 상세.
    @GetMapping("/{orderId:\\d+}")
    public String detail(
            @PathVariable("orderId") long orderId,
            @AuthenticationPrincipal MemberDetails member,
            Model model
    ) {
        long memberId = requireMemberId(member);
        model.addAttribute("orders", orderCustomerService.getMemberOrders(memberId));
        model.addAttribute("order", orderCustomerService.getMemberOrder(memberId, orderId));
        return "customer/order/detail";
    }


    /** 클라이언트 값을 통해 DB정보를 활용해 주문서 화면용 데이터 구성.**/
    private String renderGeneralOrderForm(OrderGeneralCreateForm form, Model model, long memberId) {
        var checkout = orderCheckoutService.getGeneralCheckout(
                form.getProductId(), form.getQuantity(), form.getOptionIds()
        );

        form.setDisplayedOriginalAmount(checkout.totalAmount());

        model.addAttribute("checkout", checkout);
        model.addAttribute(
                "availableCoupons",
                couponOrderQueryService.getAvailableCouponsForMember(memberId, checkout.totalAmount())
        );
        return "customer/order/form";
    }

    private String renderCustomOrderForm(OrderCustomCreateForm form, Model model, long memberId) {
        var checkout = orderCheckoutService.getCustomCheckout(
                form.getProductId(),
                form.getOptionIds()
        );
        form.setDisplayedOriginalAmount(checkout.totalAmount());
        model.addAttribute("checkout", checkout);
        model.addAttribute(
                "availableCoupons",
                couponOrderQueryService.getAvailableCouponsWithPositiveFinalAmountForMember(
                        memberId,
                        checkout.totalAmount()
                )
        );
        return "customer/order/custom-request";
    }

    private String renderCartOrderForm(OrderCartCreateForm form, Model model, long memberId) {
        var cartItems = cartOrderQueryService.getSelectedOrderItems(memberId, form.getCartItemIds());
        var checkout = orderCheckoutService.getCartCheckout(cartItems);
        form.setDisplayedOriginalAmount(checkout.totalAmount());
        model.addAttribute("orderForm", form);
        model.addAttribute("checkout", checkout);
        model.addAttribute(
                "availableCoupons",
                couponOrderQueryService.getAvailableCouponsForMember(memberId, checkout.totalAmount())
        );
        return "customer/order/cart-form";
    }

    private String paymentRedirectOrPendingGuide(
            OrderCreationResult result,
            Model model,
            String newOrderUrl
    ) {
        if (!result.requiresPendingPaymentGuide()) {
            return "redirect:/orders/" + result.orderId() + "/payment";
        }
        model.addAttribute("pendingOrder", result.pendingPaymentOrder());
        model.addAttribute("newOrderUrl", newOrderUrl);
        return "customer/order/pending-payment";
    }

    private String generalOrderFormUrl(OrderGeneralCreateForm form) {
        return UriComponentsBuilder.fromPath("/orders/checkout")
                .queryParam("productId", form.getProductId())
                .queryParam("quantity", form.getQuantity())
                .queryParam("optionIds", form.getOptionIds())
                .build()
                .encode()
                .toUriString();
    }

    private String cartOrderFormUrl(OrderCartCreateForm form) {
        return UriComponentsBuilder.fromPath("/orders/checkout/cart")
                .queryParam("itemIds", form.getCartItemIds())
                .build()
                .encode()
                .toUriString();
    }

    private String customOrderFormUrl(OrderCustomCreateForm form) {
        return UriComponentsBuilder.fromPath("/orders/custom/request")
                .queryParam("productId", form.getProductId())
                .queryParam("optionIds", form.getOptionIds())
                .build()
                .encode()
                .toUriString();
    }

    /** 유효한 아이템인지 확인. **/
    private boolean hasInvalidOrderItem(BindingResult bindingResult) {
        return bindingResult.hasFieldErrors("productId")
                || bindingResult.hasFieldErrors("quantity")
                || bindingResult.hasFieldErrors("optionIds");
    }

    /** 최초 주문서 진입 시 최신 회원 연락처를 주문자·픽업자 기본값으로 사용한다. */
    private void prefillMemberContact(
            OrderGeneralCreateForm form,
            MemberDetails member
    ) {
        MemberProfileView profile = memberService.getMemberProfile(
                member.getUsername()
        );
        form.setOrdererName(profile.name());
        form.setOrdererPhone(profile.phone());
        form.setPickupName(profile.name());
        form.setPickupPhone(profile.phone());
    }

    private void prefillMemberContact(OrderCartCreateForm form, MemberDetails member) {
        MemberProfileView profile = memberService.getMemberProfile(member.getUsername());
        form.setOrdererName(profile.name());
        form.setOrdererPhone(profile.phone());
        form.setPickupName(profile.name());
        form.setPickupPhone(profile.phone());
    }

    private void prefillMemberContact(
            OrderCustomCreateForm form,
            MemberDetails member
    ) {
        MemberProfileView profile = memberService.getMemberProfile(member.getUsername());
        form.setOrdererName(profile.name());
        form.setOrdererPhone(profile.phone());
        form.setPickupName(profile.name());
        form.setPickupPhone(profile.phone());
    }

    /** 멤버 유효한지 체크.
     * member_id 반환**/
    private long requireMemberId(MemberDetails member) {
        if (member == null || member.getMemberId() == null) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN);
        }
        return member.getMemberId();
    }
}
