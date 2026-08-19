package com.cakeshop.domain.store.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cakeshop.domain.store.dto.view.StorePublicView;
import com.cakeshop.domain.store.error.StoreErrorCode;
import com.cakeshop.domain.store.service.StoreService;
import com.cakeshop.global.error.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.ModelAndView;

@ExtendWith(MockitoExtension.class)
class StorePublicModelInterceptorTests {

    private static final StorePublicView STORE = new StorePublicView(
        "케이크 공방", "매장 소개", null, "서울시 강남구", "02-000-0000",
        "평일 10:00 ~ 20:00 / 주말 11:00 ~ 21:00", "일요일 휴무",
        "1층 픽업 데스크", "10:00 ~ 20:00"
    );

    @Mock
    private StoreService storeService;

    private StorePublicModelInterceptor interceptor;

    @BeforeEach
    void setUp() {
        interceptor = new StorePublicModelInterceptor(storeService);
    }

    @Test
    void customerView_withoutStore_addsPublicStoreToModel() throws Exception {
        when(storeService.getPublicStore()).thenReturn(STORE);
        ModelAndView modelAndView = new ModelAndView("customer/product/list");

        postHandle(modelAndView);

        assertThat(modelAndView.getModel()).containsEntry("store", STORE);
    }

    @Test
    void customerView_withStore_doesNotQueryStoreAgain() throws Exception {
        ModelAndView modelAndView = new ModelAndView("customer/product/list");
        modelAndView.addObject("store", STORE);

        postHandle(modelAndView);

        verify(storeService, never()).getPublicStore();
    }

    @Test
    void adminView_doesNotQueryStore() throws Exception {
        postHandle(new ModelAndView("admin/store/form"));

        verify(storeService, never()).getPublicStore();
    }

    @Test
    void customerView_withoutDefaultStore_keepsOriginalViewRenderable() throws Exception {
        when(storeService.getPublicStore())
            .thenThrow(new BusinessException(StoreErrorCode.NOT_FOUND));
        ModelAndView modelAndView = new ModelAndView("auth/login");

        postHandle(modelAndView);

        assertThat(modelAndView.getModel()).doesNotContainKey("store");
    }

    @Test
    void customerView_withUnexpectedStoreFailure_propagatesFailure() {
        BusinessException failure = new BusinessException(StoreErrorCode.UPDATE_FAILED);
        when(storeService.getPublicStore()).thenThrow(failure);
        ModelAndView modelAndView = new ModelAndView("auth/login");

        assertThatThrownBy(() -> postHandle(modelAndView)).isSameAs(failure);
    }

    private void postHandle(ModelAndView modelAndView) throws Exception {
        interceptor.postHandle(
            new MockHttpServletRequest(),
            new MockHttpServletResponse(),
            new Object(),
            modelAndView
        );
    }
}
