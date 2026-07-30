package com.cakeshop.domain.coupon.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cakeshop.domain.coupon.dto.form.CouponCreateForm;
import com.cakeshop.domain.coupon.dto.form.CouponSearchCondition;
import com.cakeshop.domain.coupon.dto.form.CouponUpdateForm;
import com.cakeshop.domain.coupon.dto.view.CouponView;
import com.cakeshop.domain.coupon.entity.Coupon;
import com.cakeshop.domain.coupon.entity.CouponStatus;
import com.cakeshop.domain.coupon.error.CouponErrorCode;
import com.cakeshop.domain.coupon.mapper.CouponMapper;
import com.cakeshop.global.common.paging.PageRequest;
import com.cakeshop.global.common.paging.PageResult;
import com.cakeshop.global.error.BusinessException;

/** 관리자 쿠폰의 등록·조회·수정 및 발급 상태 전환을 담당한다. */
@Service
public class CouponAdminService {

    private final CouponMapper couponMapper;

    public CouponAdminService(CouponMapper couponMapper) {
        this.couponMapper = couponMapper;
    }

    /**
     * 등록 Form을 DB 모델로 옮기고, 생성자를 현재 로그인한 관리자로 기록한다.
     * 발급 수량과 초기 상태는 INSERT 문에서 DB 기본값을 사용한다.
     */
    @Transactional
    public void insertCoupon(CouponCreateForm form, Long adminId) {
        Coupon coupon = new Coupon();

        // 화면 입력값과 DB가 정하는 초기 상태·발급 수량을 분리한다.
        applyForm(coupon, form);
        coupon.setCreatedBy(adminId);

        if (couponMapper.insertCoupon(coupon) != 1) {
            throw new BusinessException(
                    CouponErrorCode.CREATE_FAILED
            );
        }

    }

    /** 검색 조건을 정규화한 뒤 COUNT와 목록 쿼리에 동일하게 적용한다. */
    @Transactional(readOnly = true)
    public PageResult<CouponView> getCoupons(
            CouponSearchCondition condition,
            PageRequest pageRequest) {

        // 검색 조건이 없으면 전체 조회용 기본 조건을 만들고, 키워드는 한 번만 정규화한다.
        CouponSearchCondition searchCondition =
                condition == null ? new CouponSearchCondition() : condition;

        searchCondition.setKeyword(
                searchCondition.normalizedKeyword()
        );

        // COUNT와 목록 쿼리에 같은 조건을 사용해야 페이지 수와 실제 결과가 일치한다.
        long totalElements = couponMapper.countCoupons(searchCondition);

        List<CouponView> coupons = totalElements == 0
                ? List.of()
                : couponMapper.findCoupons(
                searchCondition,
                pageRequest.getSize(),
                pageRequest.getOffset()
        );

        return new PageResult<>(
                coupons,
                pageRequest,
                totalElements
        );
    }

    /**
     * 수정 화면에 필요한 값을 반환한다.
     * 종료 쿠폰은 수정 대상이 아니며, 시작 시각을 기준으로 전체/제한 수정 범위를 계산한다.
     */
    @Transactional(readOnly = true)
    public CouponUpdateForm getUpdateForm(Long couponId) {
        Coupon coupon = findCoupon(couponId);

        if (coupon.getStatus() == CouponStatus.ENDED) {
            throw new BusinessException(CouponErrorCode.CANNOT_EDIT_ENDED_COUPON);
        }

        CouponUpdateForm form = CouponUpdateForm.from(coupon);
        // 시작 전에는 정책을 자유롭게 바꿀 수 있고, 시작 후에는 발급 조건 변경을 막는다.
        boolean isFullEdit = coupon.getStartsAt().isAfter(LocalDateTime.now());
        form.setFullEdit(isFullEdit);

        return form;
    }

    /** 이미 발급한 수량보다 총 발급 수량을 낮추지 못하도록 검증한 뒤 수정한다. */
    @Transactional
    public void updateCoupon(
            Long couponId,
            CouponUpdateForm form) {

        Coupon coupon = findCoupon(couponId);

        boolean isFullEdit = validateUpdate(coupon, form);

        applyForm(coupon, form);

        int updated = isFullEdit
                ? couponMapper.updateCouponBeforeStart(coupon)
                : couponMapper.updateCouponAfterStart(coupon);

        if (updated != 1) {
            // 조회와 UPDATE 사이에 상태·시각·발급 수량이 달라졌다면 최신 상태로 다시 판정한다.
            validateUpdate(findCoupon(couponId), form);
            throw new BusinessException(CouponErrorCode.UPDATE_FAILED);
        }
    }

    /** 발급 중인 쿠폰을 중지 상태로 전환한다. */
    @Transactional
    public void deactivateCoupon(Long couponId) {
        Coupon coupon = findCoupon(couponId);

        if (coupon.getStatus() != CouponStatus.ACTIVE) {
            throw new BusinessException(CouponErrorCode.NOT_ACTIVE);
        }

        if (couponMapper.updateStatus(
                couponId,
                CouponStatus.INACTIVE
        ) != 1) {
            throw new BusinessException(
                    CouponErrorCode.DEACTIVATE_FAILED
            );
        }
    }

    /** 중지 상태이며 아직 만료되지 않은 쿠폰만 발급 상태로 전환한다. */
    @Transactional
    public void activateCoupon(Long couponId) {
        Coupon coupon = findCoupon(couponId);

        if (coupon.getStatus() != CouponStatus.INACTIVE) {
            throw new BusinessException(CouponErrorCode.NOT_INACTIVE);
        }

        // 스케줄러 실행 전이라도 만료 시각이 지났다면 재개를 막는다.
        if (!coupon.getExpiresAt().isAfter(LocalDateTime.now())) {
            throw new BusinessException(CouponErrorCode.EXPIRED_COUPON);
        }

        if (couponMapper.updateStatus(
                couponId,
                CouponStatus.ACTIVE
        ) != 1) {
            throw new BusinessException(
                    CouponErrorCode.ACTIVATE_FAILED
            );
        }
    }

    /** 스케줄러가 호출하는 만료 일괄 처리이며, 실제 갱신 건수를 반환한다. */
    @Transactional
    public int endExpiredCoupons() {
        // 스케줄러가 호출하는 일괄 상태 전이이며, 반환값은 갱신된 행 수다.
        return couponMapper.endExpiredCoupons();
    }

    /** 수정·상태 전환 전에 현재 DB 상태를 조회하고, 없으면 도메인 예외로 바꾼다. */
    private Coupon findCoupon(Long couponId) {
        return couponMapper.findCouponById(couponId)
                .orElseThrow(() -> new BusinessException(
                        CouponErrorCode.NOT_FOUND
                ));
    }

    /**
     * 현재 DB 상태를 기준으로 수정 가능 범위를 검증하고, 전체 수정 여부를 반환한다.
     * 반환값은 SQL의 전체 수정/제한 수정 분기를 결정하며, SQL도 같은 조건을 다시 확인한다.
     */
    private boolean validateUpdate(Coupon coupon, CouponUpdateForm form) {
        if (coupon.getStatus() == CouponStatus.ENDED) {
            throw new BusinessException(CouponErrorCode.CANNOT_EDIT_ENDED_COUPON);
        }

        if (form.getTotalQuantity() < coupon.getIssuedQuantity()) {
            throw new BusinessException(CouponErrorCode.QUANTITY_BELOW_ISSUED);
        }

        boolean isFullEdit = coupon.getStartsAt().isAfter(LocalDateTime.now());
        if (isFullEdit) {
            return true;
        }

        if (coupon.getDiscountType() != form.getDiscountType()
                || !sameAmount(coupon.getDiscountValue(), form.getDiscountValue())
                || !sameAmount(coupon.getMinimumOrderAmount(), form.getMinimumOrderAmount())
                || !sameAmount(coupon.getMaximumDiscountAmount(), form.getMaximumDiscountAmount())
                || !coupon.getStartsAt().equals(form.getStartsAt())) {
            throw new BusinessException(CouponErrorCode.CANNOT_MODIFY_FIELDS);
        }

        // 종료 일시는 기존 종료 일시보다 이전이면 안 되며, 동일 시각은 허용한다.
        if (form.getExpiresAt() == null || coupon.getExpiresAt() == null
                || form.getExpiresAt().isBefore(coupon.getExpiresAt())) {
            throw new BusinessException(CouponErrorCode.EXPIRES_AT_EXTENSION_ONLY);
        }

        return false;
    }

    /** 두 할인 금액이 모두 null인 경우도 같은 값으로 취급한다. */
    private boolean sameAmount(java.math.BigDecimal left, java.math.BigDecimal right) {
        if (left == null || right == null) {
            return left == right;
        }
        return left.compareTo(right) == 0;
    }

    /** 등록/수정 화면에서 변경 가능한 항목만 Entity에 복사한다. */
    private void applyForm(
            Coupon coupon,
            CouponCreateForm form) {

        coupon.setName(form.getName().trim());
        coupon.setDiscountType(form.getDiscountType());
        coupon.setDiscountValue(form.getDiscountValue());
        coupon.setMinimumOrderAmount(form.getMinimumOrderAmount());

        // 정액 할인에서는 null을 허용하고, 비율 할인 필수 여부는 Form 교차 검증이 담당한다.
        coupon.setMaximumDiscountAmount(
                form.getMaximumDiscountAmount()
        );

        coupon.setTotalQuantity(
                Math.toIntExact(form.getTotalQuantity())
        );

        coupon.setStartsAt(form.getStartsAt());
        coupon.setExpiresAt(form.getExpiresAt());
    }

    /**
     * 종료 쿠폰도 포함해 상세 화면에 필요한 Form을 반환한다.
     * 수정 화면 조회와 달리 ENDED 상태를 예외로 처리하지 않는다.
     */
    @Transactional(readOnly = true)
    public CouponUpdateForm getDetailCoupon(Long couponId) {
        Coupon coupon = findCoupon(couponId);
        CouponUpdateForm form = CouponUpdateForm.from(coupon);
        boolean isFullEdit = coupon.getStartsAt().isAfter(LocalDateTime.now());
        form.setFullEdit(isFullEdit);
        return form;
    }
}
