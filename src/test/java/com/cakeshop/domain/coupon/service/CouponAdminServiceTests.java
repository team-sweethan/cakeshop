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
import com.cakeshop.domain.coupon.dto.view.CouponIssueCandidateView;
import com.cakeshop.domain.coupon.entity.Coupon;
import com.cakeshop.domain.coupon.entity.CouponDisplayStatus;
import com.cakeshop.domain.coupon.entity.CouponStatus;
import com.cakeshop.domain.coupon.entity.CouponTargetType;
import com.cakeshop.domain.coupon.entity.DiscountType;
import com.cakeshop.domain.coupon.error.CouponErrorCode;
import com.cakeshop.domain.coupon.mapper.CouponMapper;
import com.cakeshop.domain.member.service.MemberCouponQueryService;
import com.cakeshop.domain.member.dto.view.MemberCouponView;
import com.cakeshop.global.common.paging.PageRequest;
import com.cakeshop.global.common.paging.PageResult;
import com.cakeshop.global.error.BusinessException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalDate;
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

    @Mock
    private CouponIssueService couponIssueService;

    @Mock
    private MemberCouponQueryService memberCouponQueryService;

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
    void insertAutomaticTargetStoresNullTotalQuantity() {
        CouponCreateForm form = createForm();
        form.setTargetType(CouponTargetType.ALL_MEMBERS);
        form.setTotalQuantity(null);
        when(couponMapper.insertCoupon(any())).thenReturn(1);

        couponAdminService.insertCoupon(form, 7L);

        ArgumentCaptor<Coupon> couponCaptor = ArgumentCaptor.forClass(Coupon.class);
        verify(couponMapper).insertCoupon(couponCaptor.capture());
        assertThat(couponCaptor.getValue().getTotalQuantity()).isNull();
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
    void searchTargetMembers_masksPhoneAndReturnsOnlyMonthAndDay() {
        MemberCouponView member = new MemberCouponView(
                1L, "홍길동", "member@example.com", "010-1234-5678", LocalDate.of(2000, 1, 15)
        );
        PageRequest request = new PageRequest(1, 5);
        when(memberCouponQueryService.searchActiveMembers(any(), any())).thenReturn(
                new PageResult<>(List.of(member), request, 1)
        );
        when(couponMapper.findIssuedMemberIds(3L, List.of(1L))).thenReturn(List.of());

        PageResult<CouponIssueCandidateView> result = couponAdminService.searchTargetMembers(3L, "홍", 1);

        CouponIssueCandidateView candidate = result.getContent().getFirst();
        assertThat(candidate.email()).isEqualTo("me***@example.com");
        assertThat(candidate.phone()).isEqualTo("010-****-5678");
        assertThat(candidate.birthday()).isEqualTo("01-15");
    }

    @Test
    void issueSpecificMemberRejectsCouponBeforeStart() {
        Coupon coupon = coupon(CouponStatus.ACTIVE, 10, 0, LocalDateTime.now().plusDays(1));
        coupon.setStartsAt(LocalDateTime.now().plusHours(1));
        when(couponMapper.findCouponByIdForUpdate(1L)).thenReturn(Optional.of(coupon));

        assertThatThrownBy(() -> couponAdminService.issueSpecificMember(1L, 2L))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(CouponErrorCode.UPDATE_FAILED);

        verify(couponMapper, never()).insertMemberCouponIfAbsent(1L, 2L, false, false);
    }

    @Test
    void issueSpecificMemberThrowsWhenTargetIsNoLongerIssuable() {
        Coupon coupon = coupon(CouponStatus.ACTIVE, 10, 0, LocalDateTime.now().plusDays(1));
        when(couponMapper.findCouponByIdForUpdate(1L)).thenReturn(Optional.of(coupon));
        when(couponMapper.insertMemberCouponIfAbsent(1L, 2L, false, false)).thenReturn(0);

        assertThatThrownBy(() -> couponAdminService.issueSpecificMember(1L, 2L))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(CouponErrorCode.ISSUE_TARGET_UNAVAILABLE);

        verify(couponMapper, never()).increaseIssuedQuantityIfAvailable(1L);
    }

    @Test
    void cancelSpecificMemberCouponRejectsExpiredCoupon() {
        Coupon coupon = coupon(CouponStatus.ACTIVE, 10, 1, LocalDateTime.now().minusSeconds(1));
        when(couponMapper.findCouponByIdForUpdate(1L)).thenReturn(Optional.of(coupon));

        assertThatThrownBy(() -> couponAdminService.cancelSpecificMemberCoupon(1L, 2L))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(CouponErrorCode.EXPIRED_COUPON);

        verify(couponMapper, never()).deleteAvailableMemberCoupon(1L, 2L);
    }

    @Test
    void cancelSpecificMemberCouponThrowsWhenIssuanceIsNoLongerAvailable() {
        Coupon coupon = coupon(CouponStatus.ACTIVE, 10, 1, LocalDateTime.now().plusDays(1));
        when(couponMapper.findCouponByIdForUpdate(1L)).thenReturn(Optional.of(coupon));
        when(couponMapper.deleteAvailableMemberCoupon(1L, 2L)).thenReturn(0);

        assertThatThrownBy(() -> couponAdminService.cancelSpecificMemberCoupon(1L, 2L))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(CouponErrorCode.ISSUE_CANCELLATION_UNAVAILABLE);

        verify(couponMapper, never()).decreaseIssuedQuantity(1L);
    }

    @Test
    void listUsesCountAndRequestedPageForMapperQuery() {
        CouponSearchCondition condition = new CouponSearchCondition();
        PageRequest pageRequest = new PageRequest(2, 10);
        CouponView coupon = new CouponView(
            1L, "여름 할인", "FIXED_AMOUNT", BigDecimal.valueOf(3000),
            BigDecimal.valueOf(10000), null, 100, 0,
            LocalDateTime.of(2026, 8, 1, 9, 0),
            LocalDateTime.of(2026, 8, 31, 23, 59),
            CouponStatus.ACTIVE, CouponDisplayStatus.ACTIVE
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

        verify(couponMapper, never()).updateCouponBeforeStart(any());
        verify(couponMapper, never()).updateCouponAfterStart(any());
    }

    @Test
    void getUpdateFormThrowsWhenCouponIsEnded() {
        Coupon coupon = coupon(CouponStatus.ACTIVE, 10, 0, LocalDateTime.now().minusDays(1));
        when(couponMapper.findCouponById(1L)).thenReturn(Optional.of(coupon));

        assertThatThrownBy(() -> couponAdminService.getUpdateForm(1L))
            .isInstanceOf(BusinessException.class)
            .extracting(exception -> ((BusinessException) exception).getErrorCode())
            .isEqualTo(CouponErrorCode.CANNOT_EDIT_ENDED_COUPON);
    }

    @Test
    void updateThrowsWhenCouponIsEnded() {
        Coupon coupon = coupon(CouponStatus.ACTIVE, 10, 0, LocalDateTime.now().minusDays(1));
        when(couponMapper.findCouponById(1L)).thenReturn(Optional.of(coupon));

        assertThatThrownBy(() -> couponAdminService.updateCoupon(1L, updateForm()))
            .isInstanceOf(BusinessException.class)
            .extracting(exception -> ((BusinessException) exception).getErrorCode())
            .isEqualTo(CouponErrorCode.CANNOT_EDIT_ENDED_COUPON);
    }

    @Test
    void updateRejectsForbiddenFieldModificationWhenLimitedEdit() {
        // startsAt이 과거이고, 이미 발급 수량(issuedQuantity)이 1인 상태
        Coupon coupon = coupon(CouponStatus.ACTIVE, 10, 1, LocalDateTime.now().plusDays(1));
        coupon.setStartsAt(LocalDateTime.now().minusDays(1));
        coupon.setDiscountType(DiscountType.FIXED_AMOUNT);
        coupon.setDiscountValue(BigDecimal.valueOf(3000));
        coupon.setMinimumOrderAmount(BigDecimal.valueOf(10000));
        when(couponMapper.findCouponById(1L)).thenReturn(Optional.of(coupon));

        CouponUpdateForm form = updateForm();
        form.setStartsAt(coupon.getStartsAt());
        // 수정 금지 필드인 할인값을 변경 시도
        form.setDiscountValue(BigDecimal.valueOf(5000));

        assertThatThrownBy(() -> couponAdminService.updateCoupon(1L, form))
            .isInstanceOf(BusinessException.class)
            .extracting(exception -> ((BusinessException) exception).getErrorCode())
            .isEqualTo(CouponErrorCode.CANNOT_MODIFY_FIELDS);
    }

    @Test
    void updateRejectsForbiddenFieldModificationWhenStartsAtIsPassedEvenIfIssuedQuantityIsZero() {
        // startsAt이 과거이고, 발급 수량(issuedQuantity)은 0인 상태
        Coupon coupon = coupon(CouponStatus.ACTIVE, 10, 0, LocalDateTime.now().plusDays(1));
        coupon.setStartsAt(LocalDateTime.now().minusDays(1));
        coupon.setDiscountType(DiscountType.FIXED_AMOUNT);
        coupon.setDiscountValue(BigDecimal.valueOf(3000));
        coupon.setMinimumOrderAmount(BigDecimal.valueOf(10000));
        when(couponMapper.findCouponById(1L)).thenReturn(Optional.of(coupon));

        CouponUpdateForm form = updateForm();
        form.setStartsAt(coupon.getStartsAt());
        // 수정 금지 필드인 할인값을 변경 시도
        form.setDiscountValue(BigDecimal.valueOf(5000));

        assertThatThrownBy(() -> couponAdminService.updateCoupon(1L, form))
            .isInstanceOf(BusinessException.class)
            .extracting(exception -> ((BusinessException) exception).getErrorCode())
            .isEqualTo(CouponErrorCode.CANNOT_MODIFY_FIELDS);
    }

    @Test
    void updateRejectsExpiryDateShorteningWhenLimitedEdit() {
        Coupon coupon = coupon(CouponStatus.ACTIVE, 10, 1, LocalDateTime.now().plusDays(2));
        coupon.setStartsAt(LocalDateTime.now().minusDays(1));
        coupon.setDiscountType(DiscountType.FIXED_AMOUNT);
        coupon.setDiscountValue(BigDecimal.valueOf(3000));
        coupon.setMinimumOrderAmount(BigDecimal.valueOf(10000));
        when(couponMapper.findCouponById(1L)).thenReturn(Optional.of(coupon));

        CouponUpdateForm form = updateForm();
        form.setStartsAt(coupon.getStartsAt());
        form.setDiscountType(coupon.getDiscountType());
        form.setDiscountValue(coupon.getDiscountValue());
        form.setMinimumOrderAmount(coupon.getMinimumOrderAmount());
        // 종료 일시를 기존 2일 뒤보다 앞당겨서 1일 뒤로 변경 시도
        form.setExpiresAt(LocalDateTime.now().plusDays(1));

        assertThatThrownBy(() -> couponAdminService.updateCoupon(1L, form))
            .isInstanceOf(BusinessException.class)
            .extracting(exception -> ((BusinessException) exception).getErrorCode())
            .isEqualTo(CouponErrorCode.EXPIRES_AT_EXTENSION_ONLY);
    }

    @Test
    void updateAllowsAllowedFieldsWhenLimitedEdit() {
        Coupon coupon = coupon(CouponStatus.ACTIVE, 10, 1, LocalDateTime.now().plusDays(2));
        coupon.setStartsAt(LocalDateTime.now().minusDays(1));
        coupon.setDiscountType(DiscountType.FIXED_AMOUNT);
        coupon.setDiscountValue(BigDecimal.valueOf(3000));
        coupon.setMinimumOrderAmount(BigDecimal.valueOf(10000));
        when(couponMapper.findCouponById(1L)).thenReturn(Optional.of(coupon));
        when(couponMapper.updateCouponAfterStart(any())).thenReturn(1);

        CouponUpdateForm form = updateForm();
        form.setStartsAt(coupon.getStartsAt());
        form.setDiscountType(coupon.getDiscountType());
        form.setDiscountValue(coupon.getDiscountValue());
        form.setMinimumOrderAmount(coupon.getMinimumOrderAmount());
        // 종료 일시 연장, 쿠폰명 변경, 총 수량 연장
        form.setName("이름 수정");
        form.setTotalQuantity(20L);
        form.setExpiresAt(LocalDateTime.now().plusDays(3));

        couponAdminService.updateCoupon(1L, form);

        verify(couponMapper).updateCouponAfterStart(any());
    }

    @Test
    void updateAllowsSameExpiryMinuteWhenStoredValueHasSecondsAndNanoseconds() {
        LocalDateTime storedExpiresAt = LocalDateTime.now()
                .plusDays(2)
                .withSecond(32)
                .withNano(123_000_000);
        Coupon coupon = coupon(CouponStatus.ACTIVE, 10, 1, storedExpiresAt);
        coupon.setStartsAt(LocalDateTime.now().minusDays(1));
        when(couponMapper.findCouponById(1L)).thenReturn(Optional.of(coupon));
        when(couponMapper.updateCouponAfterStart(any())).thenReturn(1);

        CouponUpdateForm form = updateForm();
        form.setStartsAt(coupon.getStartsAt());
        form.setDiscountType(coupon.getDiscountType());
        form.setDiscountValue(coupon.getDiscountValue());
        form.setMinimumOrderAmount(coupon.getMinimumOrderAmount());
        // 화면은 분 단위까지만 표시하므로 기존 종료 일시의 초·나노초는 전송하지 않는다.
        form.setExpiresAt(storedExpiresAt.withSecond(0).withNano(0));

        couponAdminService.updateCoupon(1L, form);

        verify(couponMapper).updateCouponAfterStart(any());
    }

    @Test
    void updateAllowsAllFieldsWhenFullEditDueToFutureStartsAt() {
        // startsAt이 미래인 상태
        Coupon coupon = coupon(CouponStatus.ACTIVE, 10, 0, LocalDateTime.now().plusDays(5));
        coupon.setStartsAt(LocalDateTime.now().plusDays(1));
        coupon.setDiscountType(DiscountType.FIXED_AMOUNT);
        coupon.setDiscountValue(BigDecimal.valueOf(3000));
        coupon.setMinimumOrderAmount(BigDecimal.valueOf(10000));
        when(couponMapper.findCouponById(1L)).thenReturn(Optional.of(coupon));
        when(couponMapper.updateCouponBeforeStart(any())).thenReturn(1);

        CouponUpdateForm form = updateForm();
        // startsAt이 미래이므로 제한을 받지 않고 할인값 변경 등이 가능해야 함
        form.setDiscountValue(BigDecimal.valueOf(5000));
        form.setStartsAt(LocalDateTime.now().plusDays(2));

        couponAdminService.updateCoupon(1L, form);

        verify(couponMapper).updateCouponBeforeStart(any());
    }

    @Test
    void updateRechecksStatusWhenLimitedUpdateAffectsNoRows() {
        Coupon beforeUpdate = coupon(
            CouponStatus.ACTIVE, 10, 1, LocalDateTime.now().plusDays(2)
        );
        beforeUpdate.setStartsAt(LocalDateTime.now().minusDays(1));

        Coupon endedAfterCheck = coupon(
            CouponStatus.ACTIVE, 10, 1, LocalDateTime.now().minusSeconds(1)
        );
        when(couponMapper.findCouponById(1L)).thenReturn(
            Optional.of(beforeUpdate), Optional.of(endedAfterCheck)
        );
        when(couponMapper.updateCouponAfterStart(any())).thenReturn(0);

        CouponUpdateForm form = updateForm();
        form.setStartsAt(beforeUpdate.getStartsAt());
        form.setDiscountType(beforeUpdate.getDiscountType());
        form.setDiscountValue(beforeUpdate.getDiscountValue());
        form.setMinimumOrderAmount(beforeUpdate.getMinimumOrderAmount());
        form.setExpiresAt(beforeUpdate.getExpiresAt());

        assertThatThrownBy(() -> couponAdminService.updateCoupon(1L, form))
            .isInstanceOf(BusinessException.class)
            .extracting(exception -> ((BusinessException) exception).getErrorCode())
            .isEqualTo(CouponErrorCode.CANNOT_EDIT_ENDED_COUPON);

        verify(couponMapper).updateCouponAfterStart(any());
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
    void deactivateAllowsScheduledCouponWhenAdministratorStatusIsActive() {
        Coupon coupon = coupon(CouponStatus.ACTIVE, 10, 0, LocalDateTime.now().plusDays(1));
        coupon.setStartsAt(LocalDateTime.now().plusHours(1));
        when(couponMapper.findCouponById(1L)).thenReturn(Optional.of(coupon));
        when(couponMapper.updateStatus(1L, CouponStatus.INACTIVE)).thenReturn(1);

        couponAdminService.deactivateCoupon(1L);

        verify(couponMapper).updateStatus(1L, CouponStatus.INACTIVE);
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

    private CouponCreateForm createForm() {
        CouponCreateForm form = new CouponCreateForm();
        form.setName("여름 할인");
        form.setDiscountType(DiscountType.FIXED_AMOUNT);
        form.setDiscountValue(BigDecimal.valueOf(3000));
        form.setMinimumOrderAmount(BigDecimal.valueOf(10000));
        form.setTotalQuantity(100L);
        form.setTargetType(CouponTargetType.SPECIFIC_MEMBERS);
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
        coupon.setTargetType(CouponTargetType.SPECIFIC_MEMBERS);
        coupon.setTotalQuantity(totalQuantity);
        coupon.setIssuedQuantity(issuedQuantity);
        coupon.setStartsAt(LocalDateTime.now().minusDays(1));
        coupon.setDiscountType(DiscountType.FIXED_AMOUNT);
        coupon.setDiscountValue(BigDecimal.valueOf(3000));
        coupon.setMinimumOrderAmount(BigDecimal.valueOf(10000));
        coupon.setExpiresAt(expiresAt);
        return coupon;
    }
}
