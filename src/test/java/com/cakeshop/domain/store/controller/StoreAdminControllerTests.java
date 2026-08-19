package com.cakeshop.domain.store.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.cakeshop.domain.store.dto.form.StoreBasicInfoForm;
import com.cakeshop.domain.store.dto.form.StoreBusinessHoursForm;
import com.cakeshop.domain.store.dto.form.StorePickupInfoForm;
import com.cakeshop.domain.store.dto.view.StoreView;
import com.cakeshop.domain.store.service.StoreService;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class StoreAdminControllerTests {

    @Mock
    private StoreService storeService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new StoreAdminController(storeService)).build();
    }

    @Test
    void formLoadsUpdateFormAndHolidayList() throws Exception {
        when(storeService.getStoreView()).thenReturn(storeView());

        mockMvc.perform(get("/admin/store"))
            .andExpect(status().isOk())
            .andExpect(view().name("admin/store/form"))
            .andExpect(model().attributeExists(
                "basicInfoForm", "businessHoursForm", "pickupInfoForm",
                "holidayForm", "dayOptions", "holidays"));
    }

    @Test
    void invalidBasicInfo_rendersSameFormWithoutCallingService() throws Exception {
        when(storeService.getStoreView()).thenReturn(storeView());

        mockMvc.perform(post("/admin/store/basic-info").param("name", ""))
            .andExpect(status().isOk())
            .andExpect(view().name("admin/store/form"))
            .andExpect(model().attributeHasFieldErrors("basicInfoForm", "name", "address", "phone"))
            .andExpect(model().attributeExists("businessHoursForm", "pickupInfoForm", "holidayForm"));

        verify(storeService, never()).updateBasicInfo(any(StoreBasicInfoForm.class), any());
    }

    @Test
    void validBasicInfo_redirectsWithFlashMessage() throws Exception {
        mockMvc.perform(post("/admin/store/basic-info")
                .param("name", "스위트온 케이크")
                .param("description", "예약 케이크 전문점")
                .param("address", "서울시 OO구")
                .param("phone", "02-0000-0000"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/admin/store"))
            .andExpect(flash().attribute("successMessage", "기본 정보를 저장했습니다."));

        verify(storeService).updateBasicInfo(any(StoreBasicInfoForm.class), any());
    }

    @Test
    void invalidBusinessHours_rendersSameFormWithoutCallingService() throws Exception {
        when(storeService.getStoreView()).thenReturn(storeView());

        mockMvc.perform(post("/admin/store/business-hours"))
            .andExpect(status().isOk())
            .andExpect(view().name("admin/store/form"))
            .andExpect(model().attributeHasFieldErrors(
                "businessHoursForm",
                "weekdayOpenTime", "weekdayCloseTime", "weekendOpenTime", "weekendCloseTime"))
            .andExpect(model().attributeExists("basicInfoForm", "pickupInfoForm", "holidayForm"));

        verify(storeService, never()).updateBusinessHours(any(StoreBusinessHoursForm.class));
    }

    @Test
    void validBusinessHours_redirectsWithFlashMessage() throws Exception {
        mockMvc.perform(post("/admin/store/business-hours")
                .param("weekdayOpenTime", "10:00")
                .param("weekdayCloseTime", "20:00")
                .param("weekendOpenTime", "11:00")
                .param("weekendCloseTime", "18:00")
                .param("closedDays", "SUNDAY"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/admin/store"))
            .andExpect(flash().attribute("successMessage", "영업시간을 저장했습니다."));

        verify(storeService).updateBusinessHours(any(StoreBusinessHoursForm.class));
    }

    @Test
    void invalidPickupInfo_rendersSameFormWithoutCallingService() throws Exception {
        when(storeService.getStoreView()).thenReturn(storeView());

        mockMvc.perform(post("/admin/store/pickup-info"))
            .andExpect(status().isOk())
            .andExpect(view().name("admin/store/form"))
            .andExpect(model().attributeHasFieldErrors(
                "pickupInfoForm",
                "pickupPlace", "pickupStartTime", "pickupEndTime", "pickupIntervalMinutes"))
            .andExpect(model().attributeExists("basicInfoForm", "businessHoursForm", "holidayForm"));

        verify(storeService, never()).updatePickupInfo(any(StorePickupInfoForm.class));
    }

    @Test
    void validPickupInfo_redirectsWithFlashMessage() throws Exception {
        mockMvc.perform(post("/admin/store/pickup-info")
                .param("pickupPlace", "1층 카운터")
                .param("pickupStartTime", "10:00")
                .param("pickupEndTime", "19:00")
                .param("pickupIntervalMinutes", "60"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/admin/store"))
            .andExpect(flash().attribute("successMessage", "픽업 정보를 저장했습니다."));

        verify(storeService).updatePickupInfo(any(StorePickupInfoForm.class));
    }

    private StoreView storeView() {
        return new StoreView(
            1L, "스위트온 케이크", "소개", "/uploads/store/202607/photo.jpg", "서울시", "02-0000-0000",
            LocalTime.of(10, 0), LocalTime.of(20, 0), LocalTime.of(11, 0), LocalTime.of(18, 0),
            Set.of(), "1층 카운터", LocalTime.of(10, 0), LocalTime.of(19, 0), 60, List.of()
        );
    }
}
