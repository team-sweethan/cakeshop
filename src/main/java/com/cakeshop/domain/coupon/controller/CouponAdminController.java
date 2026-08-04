package com.cakeshop.domain.coupon.controller;

import com.cakeshop.domain.coupon.dto.form.CouponCreateForm;
import com.cakeshop.domain.coupon.dto.form.CouponSearchCondition;
import com.cakeshop.domain.coupon.dto.form.CouponUpdateForm;
import com.cakeshop.domain.coupon.dto.view.CouponView;
import com.cakeshop.domain.coupon.error.CouponErrorCode;
import com.cakeshop.domain.coupon.service.CouponAdminService;
import com.cakeshop.global.common.paging.PageNavigation;
import com.cakeshop.global.common.paging.PageRequest;
import com.cakeshop.global.common.paging.PageResult;
import com.cakeshop.global.error.BusinessException;
import com.cakeshop.global.security.MemberDetails;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.support.RequestContextUtils;

/**
 * 관리자 쿠폰 목록·등록·수정·상태 전환 요청을 처리한다.
 * 실제 업무 처리와 DB 접근은 CouponAdminService에 위임한다.
 */
@Controller
@RequestMapping("/admin/coupons")
@RequiredArgsConstructor
public class CouponAdminController {

    private static final String LIST_ORIGIN = "LIST";
    private static final String DETAIL_ORIGIN = "DETAIL";

    private final CouponAdminService couponAdminService;

    /**
     * 검색 조건과 페이지 번호를 받아 관리자 쿠폰 목록을 렌더링한다.
     * PageRequest는 잘못된 페이지와 크기를 안전한 기본값으로 보정한다.
     */
    @GetMapping
    public String coupons(@ModelAttribute CouponSearchCondition condition,
                          @RequestParam(required = false) Integer page,
                          Model model,
                          HttpServletRequest request,
                          RedirectAttributes redirectAttributes) {

        PageRequest pageRequest = new PageRequest(page, PageRequest.DEFAULT_SIZE);

        PageResult<CouponView> pageResult = couponAdminService.getCoupons(
                condition,
                pageRequest
        );

        // 상태 변경 등으로 마지막 페이지가 사라진 경우 빈 목록을 보여 주지 않고 마지막 유효 페이지로 이동한다.
        int lastPage = Math.max(pageResult.getTotalPages(), 1);
        if (pageResult.getPage() > lastPage) {
            // POST redirect가 전달한 처리 결과를 페이지 보정 redirect 뒤에도 한 번 더 노출한다.
            preserveFlashAttributes(request, redirectAttributes);
            return redirectToList(condition, lastPage, redirectAttributes);
        }

        // 목록 조회 결과와 분리해, 화면에 표시할 페이지 번호 블록만 공통 객체로 계산한다.
        PageNavigation pageNavigation =
                PageNavigation.of(
                        pageResult.getPage(),
                        pageResult.getTotalPages()
                );

        model.addAttribute("condition", condition);
        model.addAttribute("pageResult", pageResult);
        model.addAttribute("pageNavigation", pageNavigation);

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
    public String updateCouponForm(@PathVariable Long couponId,
                                   @ModelAttribute CouponSearchCondition condition,
                                   @RequestParam(required = false) Integer page,
                                   @RequestParam(defaultValue = LIST_ORIGIN) String origin,
                                   Model model,
                                   RedirectAttributes redirectAttributes) {
        try {
            // 서비스에서 종료 쿠폰을 차단하므로, 수정 화면에는 수정 가능한 쿠폰만 진입한다.
            model.addAttribute(
                    "couponForm",
                    couponAdminService.getUpdateForm(couponId)
            );
            model.addAttribute("couponId", couponId);
            model.addAttribute("formMode", "update");
            addUpdateNavigation(model, condition, page, origin);

            return "admin/coupon/form";
        } catch (BusinessException e) {
            redirectAttributes.addFlashAttribute(
                    "errorMessage",
                    e.getErrorCode().message()
            );
            return redirectAfterUpdate(couponId, condition, page, origin, redirectAttributes);
        }
    }

    /** 수정 요청을 처리한다. 업무 규칙 위반은 목록 화면의 Flash 오류로 전달한다. */
    @PostMapping("{couponId}/edit")
    public String updateCoupon(@PathVariable Long couponId,
                               @Valid @ModelAttribute("couponForm") CouponUpdateForm form,
                               BindingResult bindingResult,
                               @ModelAttribute CouponSearchCondition condition,
                               @RequestParam(required = false) Integer page,
                               @RequestParam(defaultValue = LIST_ORIGIN) String origin,
                               Model model,
                               RedirectAttributes redirectAttributes) {

        if (bindingResult.hasErrors()) {
            model.addAttribute("couponId", couponId);
            model.addAttribute("formMode", "update");
            addUpdateNavigation(model, condition, page, origin);
            try {
                // 요청 Form에는 fullEdit가 없으므로, DB 기준 수정 가능 범위를 다시 채운다.
                CouponUpdateForm originalForm = couponAdminService.getUpdateForm(couponId);
                form.setFullEdit(originalForm.isFullEdit());
            } catch (BusinessException e) {
                // 종료 처리 등으로 조회할 수 없어진 경우에는 기본값으로 렌더링한다.
            }
            return "admin/coupon/form";
        }

        try {
            couponAdminService.updateCoupon(couponId, form);

            redirectAttributes.addFlashAttribute(
                    "successMessage",
                    "쿠폰을 수정했습니다."
            );
        } catch (BusinessException e) {
            if (e.getErrorCode() == CouponErrorCode.EXPIRES_AT_EXTENSION_ONLY) {
                // 사용자가 즉시 고칠 수 있는 종료 일시 오류는 목록으로 보내지 않고 Form에 표시한다.
                bindingResult.rejectValue(
                        "expiresAt",
                        e.getErrorCode().code(),
                        e.getErrorCode().message()
                );

                // 비활성화할 필드를 결정할 수 있도록 현재 수정 범위를 다시 조회한다.
                CouponUpdateForm originalForm =
                        couponAdminService.getDetailCoupon(couponId);

                form.setFullEdit(originalForm.isFullEdit());
                model.addAttribute("couponId", couponId);
                model.addAttribute("formMode", "update");
                addUpdateNavigation(model, condition, page, origin);

                return "admin/coupon/form";
            }

            redirectAttributes.addFlashAttribute(
                    "errorMessage",
                    e.getErrorCode().message()
            );
        }

        return redirectAfterUpdate(couponId, condition, page, origin, redirectAttributes);
    }

    /** ACTIVE 쿠폰만 INACTIVE로 전환한다. */
    @PostMapping("/{couponId}/deactivate")
    public String deactivateCoupon(
            @PathVariable Long couponId,
            @ModelAttribute CouponSearchCondition condition,
            @RequestParam(required = false) Integer page,
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

        return redirectToList(condition, page, redirectAttributes);
    }

    /** INACTIVE이며 만료되지 않은 쿠폰만 ACTIVE로 되돌린다. */
    @PostMapping("/{couponId}/activate")
    public String activateCoupon(
            @PathVariable Long couponId,
            @ModelAttribute CouponSearchCondition condition,
            @RequestParam(required = false) Integer page,
            RedirectAttributes redirectAttributes) {

        try {
            // 만료 쿠폰은 서비스에서 거부하므로 관리자 발급 허용 상태로 전환할 수 없다.
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

        return redirectToList(condition, page, redirectAttributes);
    }
    /**
     * 쿠폰 기본 정보와 향후 발급 회원 기능의 안내를 보여 주는 읽기 전용 상세 화면이다.
     * 종료 쿠폰도 이 화면에서는 조회할 수 있지만 수정 버튼은 노출하지 않는다.
     */
    @GetMapping("{couponId}/detail")
    public String couponDetail(
            @PathVariable Long couponId,
            @ModelAttribute CouponSearchCondition condition,
            @RequestParam(required = false) Integer page,
            Model model) {
        model.addAttribute(
                "couponForm",
                couponAdminService.getDetailCoupon(couponId)
        );
        model.addAttribute("couponId", couponId);
        // 상세 화면의 목록 버튼이 사용자가 보던 검색 결과와 페이지로 돌아가도록 보존한다.
        model.addAttribute("condition", condition);
        model.addAttribute("page", page);

        return "admin/coupon/detail";
    }

    /** 수정 화면의 취소·완료 뒤 이동 경로를 제한된 진입 출처에 따라 결정한다. */
    private String redirectAfterUpdate(Long couponId,
                                       CouponSearchCondition condition,
                                       Integer page,
                                       String origin,
                                       RedirectAttributes redirectAttributes) {
        if (DETAIL_ORIGIN.equals(origin)) {
            addListState(condition, page, redirectAttributes);
            redirectAttributes.addAttribute("couponId", couponId);
            return "redirect:/admin/coupons/{couponId}/detail";
        }

        return redirectToList(condition, page, redirectAttributes);
    }

    /** 목록 상태를 redirect query parameter로 전달해 사용자가 보던 검색 결과를 유지한다. */
    private String redirectToList(CouponSearchCondition condition,
                                  Integer page,
                                  RedirectAttributes redirectAttributes) {
        addListState(condition, page, redirectAttributes);
        return "redirect:/admin/coupons";
    }

    private void addListState(CouponSearchCondition condition,
                              Integer page,
                              RedirectAttributes redirectAttributes) {
        redirectAttributes.addAttribute("keyword", condition.getKeyword());
        redirectAttributes.addAttribute("status", condition.getStatus());
        redirectAttributes.addAttribute("page", page);
    }

    /** 페이지 보정 redirect가 기존 FlashMap을 소비하지 않도록 다음 요청으로 다시 전달한다. */
    private void preserveFlashAttributes(HttpServletRequest request,
                                         RedirectAttributes redirectAttributes) {
        Map<String, ?> inputFlashMap = RequestContextUtils.getInputFlashMap(request);
        if (inputFlashMap != null) {
            inputFlashMap.forEach(redirectAttributes::addFlashAttribute);
        }
    }

    private void addUpdateNavigation(Model model,
                                     CouponSearchCondition condition,
                                     Integer page,
                                     String origin) {
        model.addAttribute("condition", condition);
        model.addAttribute("page", page);
        model.addAttribute("origin", DETAIL_ORIGIN.equals(origin) ? DETAIL_ORIGIN : LIST_ORIGIN);
    }
}
