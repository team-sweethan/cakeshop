package com.cakeshop.domain.coupon.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
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

import com.cakeshop.domain.coupon.dto.form.CouponCreateForm;
import com.cakeshop.domain.coupon.dto.form.CouponSearchCondition;
import com.cakeshop.domain.coupon.dto.form.CouponUpdateForm;
import com.cakeshop.domain.coupon.dto.view.CouponView;
import com.cakeshop.domain.coupon.entity.CouponStatus;
import com.cakeshop.domain.coupon.entity.DiscountType;
import com.cakeshop.domain.coupon.error.CouponErrorCode;
import com.cakeshop.domain.coupon.service.CouponAdminService;
import com.cakeshop.domain.member.entity.Member;
import com.cakeshop.domain.member.dto.view.MemberAuthenticationView;
import com.cakeshop.global.common.paging.PageRequest;
import com.cakeshop.global.common.paging.PageResult;
import com.cakeshop.global.error.BusinessException;
import com.cakeshop.global.security.MemberDetails;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class CouponAdminControllerTests {

    @Mock
    private CouponAdminService couponAdminService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(
                new CouponAdminController(couponAdminService)
            )
            .setCustomArgumentResolvers(
                new AuthenticationPrincipalArgumentResolver()
            )
            .build();
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void listBindsSearchConditionAndPaging() throws Exception {
        PageResult<CouponView> result = new PageResult<>(
            List.of(), new PageRequest(2, 10), 11
        );
        when(couponAdminService.getCoupons(any(), any())).thenReturn(result);

        mockMvc.perform(get("/admin/coupons")
                .param("keyword", " 여름 ")
                .param("status", "ACTIVE")
                .param("page", "2")
                .param("size", "10"))
            .andExpect(status().isOk())
            .andExpect(view().name("admin/coupon/list"))
            .andExpect(model().attribute("pageResult", result))
            .andExpect(model().attributeExists("condition"));

        ArgumentCaptor<CouponSearchCondition> conditionCaptor =
            ArgumentCaptor.forClass(CouponSearchCondition.class);
        ArgumentCaptor<PageRequest> pageCaptor =
            ArgumentCaptor.forClass(PageRequest.class);

        verify(couponAdminService).getCoupons(
            conditionCaptor.capture(), pageCaptor.capture()
        );

        assertThat(conditionCaptor.getValue().getKeyword()).isEqualTo(" 여름 ");
        assertThat(conditionCaptor.getValue().getStatus()).isEqualTo(CouponStatus.ACTIVE);
        assertThat(pageCaptor.getValue().getPage()).isEqualTo(2);
        assertThat(pageCaptor.getValue().getSize()).isEqualTo(10);
        assertThat(pageCaptor.getValue().getOffset()).isEqualTo(10);
    }

    @Test
    void createFormLoadsEmptyForm() throws Exception {
        mockMvc.perform(get("/admin/coupons/create"))
            .andExpect(status().isOk())
            .andExpect(view().name("admin/coupon/form"))
            .andExpect(model().attributeExists("couponForm"))
            .andExpect(model().attribute("formMode", "create"));
    }

    @Test
    void invalidCreateRendersFormWithoutCallingService() throws Exception {
        mockMvc.perform(post("/admin/coupons/create")
                .param("name", ""))
            .andExpect(status().isOk())
            .andExpect(view().name("admin/coupon/form"))
            .andExpect(model().attribute("formMode", "create"))
            .andExpect(model().attributeHasFieldErrors("couponForm", "name"));

        verify(couponAdminService, never()).insertCoupon(any(), any());
    }

    @Test
    void validCreateUsesLoggedInAdminAndRedirects() throws Exception {
        authenticateAdmin(7L);

        mockMvc.perform(post("/admin/coupons/create")
                .param("name", "여름 할인")
                .param("discountType", "FIXED_AMOUNT")
                .param("discountValue", "3000")
                .param("minimumOrderAmount", "10000")
                .param("totalQuantity", "100")
                .param("startsAt", "2026-08-01T09:00")
                .param("expiresAt", "2026-08-31T23:59"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/admin/coupons"))
            .andExpect(flash().attribute("successMessage", "쿠폰을 등록했습니다."));

        ArgumentCaptor<CouponCreateForm> formCaptor =
            ArgumentCaptor.forClass(CouponCreateForm.class);
        verify(couponAdminService).insertCoupon(formCaptor.capture(), org.mockito.ArgumentMatchers.eq(7L));

        assertThat(formCaptor.getValue().getName()).isEqualTo("여름 할인");
        assertThat(formCaptor.getValue().getDiscountType()).isEqualTo(DiscountType.FIXED_AMOUNT);
    }

    @Test
    void createFailureRedirectsWithErrorMessage() throws Exception {
        authenticateAdmin(7L);
        doThrow(new BusinessException(CouponErrorCode.CREATE_FAILED))
            .when(couponAdminService).insertCoupon(any(), org.mockito.ArgumentMatchers.eq(7L));

        mockMvc.perform(post("/admin/coupons/create")
                .param("name", "여름 할인")
                .param("discountType", "FIXED_AMOUNT")
                .param("discountValue", "3000")
                .param("minimumOrderAmount", "10000")
                .param("totalQuantity", "100")
                .param("startsAt", "2026-08-01T09:00")
                .param("expiresAt", "2026-08-31T23:59"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/admin/coupons"))
            .andExpect(flash().attribute(
                "errorMessage", CouponErrorCode.CREATE_FAILED.message()
            ));
    }

    @Test
    void editFormLoadsCouponValues() throws Exception {
        CouponUpdateForm form = validUpdateForm();
        when(couponAdminService.getUpdateForm(3L)).thenReturn(form);

        mockMvc.perform(get("/admin/coupons/3/edit"))
            .andExpect(status().isOk())
            .andExpect(view().name("admin/coupon/form"))
            .andExpect(model().attribute("couponForm", form))
            .andExpect(model().attribute("couponId", 3L))
            .andExpect(model().attribute("formMode", "update"));

        verify(couponAdminService).getUpdateForm(3L);
    }

    @Test
    void validUpdateRedirectsWithSuccessMessage() throws Exception {
        mockMvc.perform(post("/admin/coupons/3/edit")
                .param("name", "수정된 쿠폰")
                .param("discountType", "PERCENTAGE")
                .param("discountValue", "10")
                .param("minimumOrderAmount", "10000")
                .param("maximumDiscountAmount", "5000")
                .param("totalQuantity", "100")
                .param("startsAt", "2026-08-01T09:00")
                .param("expiresAt", "2026-08-31T23:59"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/admin/coupons"))
            .andExpect(flash().attribute("successMessage", "쿠폰을 수정했습니다."));

        verify(couponAdminService).updateCoupon(org.mockito.ArgumentMatchers.eq(3L), any());
    }

    @Test
    void deactivateFailureRedirectsWithErrorMessage() throws Exception {
        doThrow(new BusinessException(CouponErrorCode.NOT_ACTIVE))
            .when(couponAdminService).deactivateCoupon(3L);

        mockMvc.perform(post("/admin/coupons/3/deactivate"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/admin/coupons"))
            .andExpect(flash().attribute(
                "errorMessage", CouponErrorCode.NOT_ACTIVE.message()
            ));
    }

    @Test
    void activateFailureRedirectsWithErrorMessage() throws Exception {
        doThrow(new BusinessException(CouponErrorCode.EXPIRED_COUPON))
            .when(couponAdminService).activateCoupon(3L);

        mockMvc.perform(post("/admin/coupons/3/activate"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/admin/coupons"))
            .andExpect(flash().attribute(
                "errorMessage", CouponErrorCode.EXPIRED_COUPON.message()
            ));
    }

    private void authenticateAdmin(Long memberId) {
        Member member = Member.builder()
            .id(memberId)
            .email("admin@cakeshop.com")
            .password("encoded-password")
            .role("ADMIN")
            .build();
        MemberDetails memberDetails = new MemberDetails(new MemberAuthenticationView(
                member.getId(),
                member.getEmail(),
                member.getPassword(),
                member.getRole(),
                true));

        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(
                memberDetails, null, memberDetails.getAuthorities()
            )
        );
    }

    private CouponUpdateForm validUpdateForm() {
        CouponUpdateForm form = new CouponUpdateForm();
        form.setName("여름 할인");
        form.setDiscountType(DiscountType.FIXED_AMOUNT);
        form.setDiscountValue(BigDecimal.valueOf(3000));
        form.setMinimumOrderAmount(BigDecimal.valueOf(10000));
        form.setTotalQuantity(100L);
        form.setStartsAt(LocalDateTime.of(2026, 8, 1, 9, 0));
        form.setExpiresAt(LocalDateTime.of(2026, 8, 31, 23, 59));
        return form;
    }
}
