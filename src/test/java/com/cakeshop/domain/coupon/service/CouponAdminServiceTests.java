package com.cakeshop.domain.coupon.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cakeshop.domain.coupon.dto.form.CouponCreateForm;
import com.cakeshop.domain.coupon.dto.form.CouponSearchCondition;
import com.cakeshop.domain.coupon.dto.form.CouponUpdateForm;
import com.cakeshop.domain.coupon.dto.view.CouponView;
import com.cakeshop.domain.coupon.entity.Coupon;
import com.cakeshop.domain.coupon.entity.CouponStatus;
import com.cakeshop.domain.coupon.entity.DiscountType;
import com.cakeshop.domain.coupon.error.CouponErrorCode;
import com.cakeshop.domain.coupon.mapper.CouponMapper;
import com.cakeshop.global.common.paging.PageRequest;
import com.cakeshop.global.common.paging.PageResult;
import com.cakeshop.global.error.BusinessException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CouponAdminServiceTests {

    @Mock
    private CouponMapper couponMapper;

    @InjectMocks
    private CouponAdminService couponAdminService;

    @Test
    void insertCopiesFormValuesAndCreatorId() {
        CouponCreateForm form = createForm();
        form.setName(" 여름 할인 ");
        when(couponMapper.insertCoupon(any())).thenReturn(1);

        couponAdminService.insertCoupon(form, 7L);

        ArgumentCaptor<Coupon> couponCaptor = ArgumentCaptor.forClass(Coupon.class);
        verify(couponMapper).insertCoupon(couponCaptor.capture());

        Coupon saved = couponCaptor.getValue();
        assertThat(saved.getName()).isEqualTo("여름 할인");
        assertThat(saved.getDiscountType()).isEqualTo(DiscountType.FIXED_AMOUNT);
        assertThat(saved.getDiscountValue()).isEqualByComparingTo("3000");
        assertThat(saved.getMinimumOrderAmount()).isEqualByComparingTo("10000");
        assertThat(saved.getTotalQuantity()).isEqualTo(100);
        assertThat(saved.getCreatedBy()).isEqualTo(7L);
    }

    @Test
    void insertThrowsWhenMapperDoesNotInsert() {
        when(couponMapper.insertCoupon(any())).thenReturn(0);

        assertThatThrownBy(() -> couponAdminService.insertCoupon(createForm(), 7L))
            .isInstanceOf(BusinessException.class)
            .extracting(exception -> ((BusinessException) exception).getErrorCode())
            .isEqualTo(CouponErrorCode.CREATE_FAILED);
    }

    @Test
    void emptyListNormalizesBlankKeywordAndSkipsListQuery() {
        CouponSearchCondition condition = new CouponSearchCondition();
        condition.setKeyword("   ");
        PageRequest pageRequest = new PageRequest(1, 20);
        when(couponMapper.countCoupons(any())).thenReturn(0L);

        PageResult<CouponView> result = couponAdminService.getCoupons(
            condition, pageRequest
        );

        ArgumentCaptor<CouponSearchCondition> conditionCaptor =
            ArgumentCaptor.forClass(CouponSearchCondition.class);
        verify(couponMapper).countCoupons(conditionCaptor.capture());

        assertThat(conditionCaptor.getValue().getKeyword()).isNull();
        verify(couponMapper, never()).findCoupons(any(), anyInt(), anyInt());
        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isZero();
    }

    @Test
    void listUsesCountAndRequestedPageForMapperQuery() {
        CouponSearchCondition condition = new CouponSearchCondition();
        PageRequest pageRequest = new PageRequest(2, 10);
        CouponView coupon = new CouponView(
            1L, "여름 할인", "FIXED_AMOUNT", BigDecimal.valueOf(3000),
            BigDecimal.valueOf(10000), null, 100, 0,
            LocalDateTime.of(2026, 8, 1, 9, 0),
            LocalDateTime.of(2026, 8, 31, 23, 59), "ACTIVE"
        );
        when(couponMapper.countCoupons(condition)).thenReturn(11L);
        when(couponMapper.findCoupons(condition, 10, 10)).thenReturn(List.of(coupon));

        PageResult<CouponView> result = couponAdminService.getCoupons(
            condition, pageRequest
        );

        assertThat(result.getContent()).containsExactly(coupon);
        assertThat(result.getPage()).isEqualTo(2);
        assertThat(result.getTotalPages()).isEqualTo(2);
    }

    @Test
    void updateRejectsTotalQuantityBelowIssuedQuantity() {
        Coupon coupon = coupon(CouponStatus.ACTIVE, 10, 5,
            LocalDateTime.now().plusDays(1));
        when(couponMapper.findCouponById(1L)).thenReturn(Optional.of(coupon));

        CouponUpdateForm form = updateForm();
        form.setTotalQuantity(4L);

        assertThatThrownBy(() -> couponAdminService.updateCoupon(1L, form))
            .isInstanceOf(BusinessException.class)
            .extracting(exception -> ((BusinessException) exception).getErrorCode())
            .isEqualTo(CouponErrorCode.QUANTITY_BELOW_ISSUED);

        verify(couponMapper, never()).updateCoupon(any());
    }

    @Test
    void deactivateChangesActiveCouponToInactive() {
        when(couponMapper.findCouponById(1L)).thenReturn(Optional.of(
            coupon(CouponStatus.ACTIVE, 10, 0, LocalDateTime.now().plusDays(1))
        ));
        when(couponMapper.updateStatus(1L, CouponStatus.INACTIVE)).thenReturn(1);

        couponAdminService.deactivateCoupon(1L);

        verify(couponMapper).updateStatus(1L, CouponStatus.INACTIVE);
    }

    @Test
    void deactivateRejectsCouponThatIsNotActive() {
        when(couponMapper.findCouponById(1L)).thenReturn(Optional.of(
            coupon(CouponStatus.INACTIVE, 10, 0, LocalDateTime.now().plusDays(1))
        ));

        assertThatThrownBy(() -> couponAdminService.deactivateCoupon(1L))
            .isInstanceOf(BusinessException.class)
            .extracting(exception -> ((BusinessException) exception).getErrorCode())
            .isEqualTo(CouponErrorCode.NOT_ACTIVE);

        verify(couponMapper, never()).updateStatus(any(), any());
    }

    @Test
    void activateRejectsExpiredInactiveCoupon() {
        when(couponMapper.findCouponById(1L)).thenReturn(Optional.of(
            coupon(CouponStatus.INACTIVE, 10, 0, LocalDateTime.now().minusSeconds(1))
        ));

        assertThatThrownBy(() -> couponAdminService.activateCoupon(1L))
            .isInstanceOf(BusinessException.class)
            .extracting(exception -> ((BusinessException) exception).getErrorCode())
            .isEqualTo(CouponErrorCode.EXPIRED_COUPON);

        verify(couponMapper, never()).updateStatus(any(), any());
    }

    @Test
    void activateChangesFutureInactiveCouponToActive() {
        when(couponMapper.findCouponById(1L)).thenReturn(Optional.of(
            coupon(CouponStatus.INACTIVE, 10, 0, LocalDateTime.now().plusDays(1))
        ));
        when(couponMapper.updateStatus(1L, CouponStatus.ACTIVE)).thenReturn(1);

        couponAdminService.activateCoupon(1L);

        verify(couponMapper).updateStatus(1L, CouponStatus.ACTIVE);
    }

    @Test
    void endExpiredCouponsReturnsUpdatedCount() {
        when(couponMapper.endExpiredCoupons()).thenReturn(3);

        int updatedCount = couponAdminService.endExpiredCoupons();

        assertThat(updatedCount).isEqualTo(3);
        verify(couponMapper).endExpiredCoupons();
    }

    private CouponCreateForm createForm() {
        CouponCreateForm form = new CouponCreateForm();
        form.setName("여름 할인");
        form.setDiscountType(DiscountType.FIXED_AMOUNT);
        form.setDiscountValue(BigDecimal.valueOf(3000));
        form.setMinimumOrderAmount(BigDecimal.valueOf(10000));
        form.setTotalQuantity(100L);
        form.setStartsAt(LocalDateTime.of(2026, 8, 1, 9, 0));
        form.setExpiresAt(LocalDateTime.of(2026, 8, 31, 23, 59));
        return form;
    }

    private CouponUpdateForm updateForm() {
        CouponUpdateForm form = new CouponUpdateForm();
        form.setName("수정된 할인");
        form.setDiscountType(DiscountType.FIXED_AMOUNT);
        form.setDiscountValue(BigDecimal.valueOf(3000));
        form.setMinimumOrderAmount(BigDecimal.valueOf(10000));
        form.setTotalQuantity(10L);
        form.setStartsAt(LocalDateTime.of(2026, 8, 1, 9, 0));
        form.setExpiresAt(LocalDateTime.of(2026, 8, 31, 23, 59));
        return form;
    }

    private Coupon coupon(
            CouponStatus status,
            int totalQuantity,
            int issuedQuantity,
            LocalDateTime expiresAt) {
        Coupon coupon = new Coupon();
        coupon.setId(1L);
        coupon.setName("여름 할인");
        coupon.setStatus(status);
        coupon.setTotalQuantity(totalQuantity);
        coupon.setIssuedQuantity(issuedQuantity);
        coupon.setExpiresAt(expiresAt);
        return coupon;
    }
}
