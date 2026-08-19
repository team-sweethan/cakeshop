package com.cakeshop.domain.store.controller;

import com.cakeshop.domain.store.error.StoreErrorCode;
import com.cakeshop.domain.store.service.StoreService;
import com.cakeshop.global.error.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Set;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

/** 고객 공통 화면을 렌더링할 때 푸터에 사용할 공개 매장 정보를 제공한다. */
public class StorePublicModelInterceptor implements HandlerInterceptor {

    private static final String STORE_MODEL_ATTRIBUTE = "store";
    private static final Set<String> STORE_FOOTER_VIEWS = Set.of(
        "auth/login",
        "auth/oauth-signup",
        "customer/cart/list",
        "customer/chat/room",
        "customer/community/detail",
        "customer/community/form",
        "customer/community/list",
        "customer/community/notice/detail",
        "customer/community/notice/list",
        "customer/coupon/list",
        "customer/member/find-email",
        "customer/member/find-password",
        "customer/member/mypage",
        "customer/member/profile-edit",
        "customer/member/reset-password",
        "customer/member/signup",
        "customer/notification/list",
        "customer/order/cart-form",
        "customer/order/complete",
        "customer/order/custom-option",
        "customer/order/custom-request",
        "customer/order/detail",
        "customer/order/form",
        "customer/order/pickup-setting",
        "customer/payment/form",
        "customer/product/detail",
        "customer/product/list",
        "customer/review/edit",
        "customer/review/form",
        "customer/review/my",
        "customer/review/product",
        "customer/review/writable",
        "home/main"
    );

    private final StoreService storeService;

    public StorePublicModelInterceptor(StoreService storeService) {
        this.storeService = storeService;
    }

    @Override
    public void postHandle(HttpServletRequest request,
                           HttpServletResponse response,
                           Object handler,
                           ModelAndView modelAndView) {
        if (!supports(modelAndView) || modelAndView.getModel().containsKey(STORE_MODEL_ATTRIBUTE)) {
            return;
        }

        try {
            modelAndView.addObject(STORE_MODEL_ATTRIBUTE, storeService.getPublicStore());
        } catch (BusinessException exception) {
            if (exception.getErrorCode() != StoreErrorCode.NOT_FOUND) {
                throw exception;
            }
            // 대표 매장이나 영업시간이 준비되지 않아도 고객의 본래 요청은 정상 렌더링한다.
        }
    }

    private boolean supports(ModelAndView modelAndView) {
        if (modelAndView == null || modelAndView.getViewName() == null) {
            return false;
        }

        return STORE_FOOTER_VIEWS.contains(modelAndView.getViewName());
    }
}
