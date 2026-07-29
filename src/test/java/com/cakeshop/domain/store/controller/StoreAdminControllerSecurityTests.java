package com.cakeshop.domain.store.controller;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.cakeshop.domain.store.dto.view.StoreView;
import com.cakeshop.domain.store.service.StoreService;
import com.cakeshop.global.security.SecurityConfig;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 인증·인가 검증 예제 테스트.
 *
 * <p>docs/testing.md 2절: URL 패턴 단위 접근 제어와 CSRF는 Security 필터를 실제로 태워야 검증된다.
 * {@code standaloneSetup}은 필터를 타지 않으므로 이 검증이 아예 불가능하다.
 * 따라서 {@code @WebMvcTest} + 실제 {@link SecurityConfig} import 조합을 쓴다.
 *
 * <p>다른 화면에 적용할 때는 아래 3가지만 바꾸면 된다.
 * <ol>
 *   <li>{@code @WebMvcTest(대상Controller.class)}</li>
 *   <li>{@code @MockitoBean}으로 선언할 Service</li>
 *   <li>요청 경로와 기대 역할</li>
 * </ol>
 */
@WebMvcTest(StoreAdminController.class)
@Import(SecurityConfig.class)
class StoreAdminControllerSecurityTests {

    @Autowired
    private MockMvc mockMvc;

    /** Controller가 의존하는 Service는 실제로 띄우지 않고 대역으로 채운다. */
    @MockitoBean
    private StoreService storeService;

    @Test
    @WithAnonymousUser
    void adminStore_redirectsToLogin_whenNotAuthenticated() throws Exception {
        mockMvc.perform(get("/admin/store"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/login"));
    }

    @Test
    @WithMockUser(roles = "USER")
    void adminStore_isForbidden_forCustomerRole() throws Exception {
        // 로그인은 했지만 ADMIN이 아니면 403이다. (SecurityConfig의 /admin/** hasRole("ADMIN"))
        mockMvc.perform(get("/admin/store"))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminStore_isAccessible_forAdminRole() throws Exception {
        when(storeService.getStoreView()).thenReturn(storeView());

        mockMvc.perform(get("/admin/store"))
            .andExpect(status().isOk())
            .andExpect(view().name("admin/store/form"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void updateStore_isForbidden_whenCsrfTokenIsMissing() throws Exception {
        // 상태를 변경하는 POST는 CSRF 토큰이 없으면 Controller에 도달하지 못한다.
        mockMvc.perform(post("/admin/store").params(validStoreParams()))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void updateStore_succeeds_whenCsrfTokenIsPresent() throws Exception {
        mockMvc.perform(post("/admin/store").params(validStoreParams()).with(csrf()))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/admin/store"));
    }

    private org.springframework.util.MultiValueMap<String, String> validStoreParams() {
        var params = new org.springframework.util.LinkedMultiValueMap<String, String>();
        params.add("name", "스위트온 케이크");
        params.add("description", "예약 케이크 전문점");
        params.add("address", "서울시 OO구");
        params.add("phone", "02-0000-0000");
        params.add("weekdayOpenTime", "10:00");
        params.add("weekdayCloseTime", "20:00");
        params.add("weekendOpenTime", "11:00");
        params.add("weekendCloseTime", "18:00");
        params.add("closedDays", "SUNDAY");
        params.add("pickupPlace", "1층 카운터");
        params.add("pickupStartTime", "10:00");
        params.add("pickupEndTime", "19:00");
        params.add("pickupIntervalMinutes", "60");
        return params;
    }

    private StoreView storeView() {
        return new StoreView(
            1L, "스위트온 케이크", "소개", "/uploads/store/202607/photo.jpg", "서울시", "02-0000-0000",
            LocalTime.of(10, 0), LocalTime.of(20, 0), LocalTime.of(11, 0), LocalTime.of(18, 0),
            Set.of(), "1층 카운터", LocalTime.of(10, 0), LocalTime.of(19, 0), 60, List.of()
        );
    }
}
