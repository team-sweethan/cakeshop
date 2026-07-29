package com.cakeshop.domain.coupon.controller;

import com.cakeshop.domain.coupon.dto.form.CouponCreateForm;
import com.cakeshop.domain.coupon.dto.form.CouponSearchCondition;
import com.cakeshop.domain.coupon.dto.form.CouponUpdateForm;
import com.cakeshop.domain.coupon.dto.view.CouponView;
import com.cakeshop.domain.coupon.service.CouponAdminService;
import com.cakeshop.global.common.paging.PageRequest;
import com.cakeshop.global.common.paging.PageResult;
import com.cakeshop.global.error.BusinessException;
import com.cakeshop.global.security.MemberDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * 관리자 쿠폰 목록·등록·수정·상태 전환 요청을 처리한다.
 * 실제 업무 처리와 DB 접근은 CouponAdminService에 위임한다.
 */
@Controller
@RequestMapping("/admin/coupons")
@RequiredArgsConstructor
public class CouponAdminController {

    private final CouponAdminService couponAdminService;

    /**
     * 검색 조건과 페이지 번호를 받아 관리자 쿠폰 목록을 렌더링한다.
     * PageRequest는 잘못된 페이지와 크기를 안전한 기본값으로 보정한다.
     */
    @GetMapping
    public String coupons(@ModelAttribute CouponSearchCondition condition,
                          @RequestParam(required = false) Integer page,
                          @RequestParam(required = false) Integer size,
                          Model model) {

        PageRequest pageRequest = new PageRequest(page, size);

        PageResult<CouponView> pageResult = couponAdminService.getCoupons(
                condition,
                pageRequest
        );

        model.addAttribute("condition", condition);
        model.addAttribute("pageResult", pageResult);

        return "admin/coupon/list";
    }

    /** 새 쿠폰 등록에 사용할 빈 Form과 화면 모드를 모델에 넣는다. */
    @GetMapping("create")
    public String insertCouponForm(Model model) {
        model.addAttribute("couponForm", new CouponCreateForm());
        model.addAttribute("formMode", "create");

        return "admin/coupon/form";
    }

    /**
     * 검증에 성공한 등록 Form과 현재 로그인한 관리자의 ID를 서비스로 전달한다.
     * 검증 실패는 redirect하지 않아 사용자가 입력한 값을 그대로 다시 보여 준다.
     */
    @PostMapping("create")
    public String insertCoupons(@Valid @ModelAttribute("couponForm") CouponCreateForm form,
                         BindingResult bindingResult,
                         @AuthenticationPrincipal MemberDetails member,
                         Model model,
                         RedirectAttributes redirectAttributes) {

        if (bindingResult.hasErrors()) {
            model.addAttribute("formMode", "create");
            return "admin/coupon/form";
        }

        try {
            couponAdminService.insertCoupon(form, member.getMemberId());

            redirectAttributes.addFlashAttribute(
                    "successMessage",
                    "쿠폰을 등록했습니다."
            );
        } catch (BusinessException e) {
            redirectAttributes.addFlashAttribute(
                    "errorMessage",
                    e.getErrorCode().message()
            );
        }

        return "redirect:/admin/coupons";
    }

    /** 기존 쿠폰 값을 수정 Form으로 변환해 수정 화면에 제공한다. */
    @GetMapping("{couponId}/edit")
    public String updateCouponForm(@PathVariable Long couponId, Model model) {
        model.addAttribute(
                "couponForm",
                couponAdminService.getUpdateForm(couponId)
        );
        model.addAttribute("couponId", couponId);
        model.addAttribute("formMode", "update");

        return "admin/coupon/form";
    }

    /** 수정 요청을 처리한다. 업무 규칙 위반은 목록 화면의 Flash 오류로 전달한다. */
    @PostMapping("{couponId}/edit")
    public String updateCoupon(@PathVariable Long couponId,
                               @Valid @ModelAttribute("couponForm") CouponUpdateForm form,
                               BindingResult bindingResult,
                               Model model,
                               RedirectAttributes redirectAttributes) {

        if (bindingResult.hasErrors()) {
            model.addAttribute("couponId", couponId);
            model.addAttribute("formMode", "update");

            return "admin/coupon/form";
        }

        try {
            couponAdminService.updateCoupon(couponId, form);

            redirectAttributes.addFlashAttribute(
                    "successMessage",
                    "쿠폰을 수정했습니다."
            );
        } catch (BusinessException e) {
            redirectAttributes.addFlashAttribute(
                    "errorMessage",
                    e.getErrorCode().message()
            );
        }

        return "redirect:/admin/coupons";
    }

    /** ACTIVE 쿠폰만 INACTIVE로 전환한다. */
    @PostMapping("/{couponId}/deactivate")
    public String deactivateCoupon(
            @PathVariable Long couponId,
            RedirectAttributes redirectAttributes) {

        try {
            // 삭제 대신 INACTIVE로 전환해 발급·주문 이력을 보존한다.
            couponAdminService.deactivateCoupon(couponId);

            redirectAttributes.addFlashAttribute(
                    "successMessage",
                    "쿠폰 발급을 중지했습니다."
            );
        } catch (BusinessException e) {
            redirectAttributes.addFlashAttribute(
                    "errorMessage",
                    e.getErrorCode().message()
            );
        }

        return "redirect:/admin/coupons";
    }

    /** INACTIVE이며 만료되지 않은 쿠폰만 ACTIVE로 되돌린다. */
    @PostMapping("/{couponId}/activate")
    public String activateCoupon(
            @PathVariable Long couponId,
            RedirectAttributes redirectAttributes) {

        try {
            // 만료된 쿠폰은 서비스에서 거부하므로 ENDED를 ACTIVE로 되돌릴 수 없다.
            couponAdminService.activateCoupon(couponId);

            redirectAttributes.addFlashAttribute(
                    "successMessage",
                    "쿠폰 발급을 재개했습니다."
            );
        } catch (BusinessException e) {
            redirectAttributes.addFlashAttribute(
                    "errorMessage",
                    e.getErrorCode().message()
            );
        }

        return "redirect:/admin/coupons";
    }
}
