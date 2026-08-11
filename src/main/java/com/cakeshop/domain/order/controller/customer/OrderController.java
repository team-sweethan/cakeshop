package com.cakeshop.domain.order.controller.customer;

import com.cakeshop.domain.member.dto.view.MemberProfileView;
import com.cakeshop.domain.member.service.MemberService;
import com.cakeshop.domain.coupon.service.CouponOrderQueryService;
import com.cakeshop.domain.order.dto.form.CancelForm;
import com.cakeshop.domain.order.dto.form.customer.GeneralOrderForm;
import com.cakeshop.domain.order.error.OrderErrorCode;
import com.cakeshop.domain.order.service.customer.OrderCheckoutService;
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

    /** 장바구니 항목의 픽업 일시 수정용 목업 경로다. 일반 주문은 checkout에서 선택한다. */
    @GetMapping(value = "/pickup", params = "intent=cart-edit")
    public String pickupSetting() {
        return "customer/order/pickup-setting";
    }

    @GetMapping("/custom/options")
    public String customOptions() {
        return "customer/order/custom-option";
    }

    @GetMapping("/custom/request")
    public String customRequest() {
        return "customer/order/custom-request";
    }

    // 일반 상품 주문서 화면
    @GetMapping("/checkout")
    public String checkout(
            @ModelAttribute("orderForm") GeneralOrderForm form,
            @AuthenticationPrincipal MemberDetails member,
            Model model
    ) {
        prefillMemberContact(form, member);
        form.setRequestKey(UUID.randomUUID().toString());

        return renderGeneralOrderForm(form, model, requireMemberId(member));
    }

    // 일반 상품 주문 생성
    @PostMapping("/general")
    public String createGeneralOrder(
            @AuthenticationPrincipal MemberDetails member,
            @Valid @ModelAttribute("orderForm") GeneralOrderForm form,
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
            long orderId = orderService.createGeneralOrder(memberId, form);
            return "redirect:/orders/" + orderId + "/payment";
        } catch (BusinessException exception) {
            if (exception.getErrorCode() != OrderErrorCode.ORDER_AMOUNT_CHANGED) {
                throw exception;
            }
            bindingResult.reject("orderAmountChanged", exception.getErrorCode().message());
            return renderGeneralOrderForm(form, model, memberId);
        }
    }

    // 로그인 회원의 주문 취소
    @PostMapping("/{orderId}/cancel")
    public String cancel(
            @PathVariable("orderId") long orderId,
            @AuthenticationPrincipal MemberDetails member,
            @Valid @ModelAttribute CancelForm form,
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
    private String renderGeneralOrderForm(GeneralOrderForm form, Model model, long memberId) {
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

    /** 유효한 아이템인지 확인. **/
    private boolean hasInvalidOrderItem(BindingResult bindingResult) {
        return bindingResult.hasFieldErrors("productId")
                || bindingResult.hasFieldErrors("quantity")
                || bindingResult.hasFieldErrors("optionIds");
    }

    /** 최초 주문서 진입 시 최신 회원 연락처를 주문자·픽업자 기본값으로 사용한다. */
    private void prefillMemberContact(
            GeneralOrderForm form,
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

    /** 멤버 유효한지 체크.
     * member_id 반환**/
    private long requireMemberId(MemberDetails member) {
        if (member == null || member.getMemberId() == null) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN);
        }
        return member.getMemberId();
    }
}
