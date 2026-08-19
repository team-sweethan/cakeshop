package com.cakeshop.domain.store.controller;

import com.cakeshop.domain.store.error.StoreErrorCode;
import com.cakeshop.domain.store.service.StoreService;
import com.cakeshop.global.error.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

/** 고객 공통 화면을 렌더링할 때 푸터에 사용할 공개 매장 정보를 제공한다. */
public class StorePublicModelInterceptor implements HandlerInterceptor {

    private static final String STORE_MODEL_ATTRIBUTE = "store";

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

        String viewName = modelAndView.getViewName();
        return viewName.startsWith("customer/")
            || viewName.startsWith("auth/")
            || viewName.startsWith("home/");
    }
}
