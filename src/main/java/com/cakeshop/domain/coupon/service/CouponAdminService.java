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

    /** 수정 화면에 필요한 값만 담은 Form을 반환한다. */
    @Transactional(readOnly = true)
    public CouponUpdateForm getUpdateForm(Long couponId) {
        Coupon coupon = findCoupon(couponId);
        return CouponUpdateForm.from(coupon);
    }

    /** 이미 발급한 수량보다 총 발급 수량을 낮추지 못하도록 검증한 뒤 수정한다. */
    @Transactional
    public void updateCoupon(
            Long couponId,
            CouponUpdateForm form) {

        Coupon coupon = findCoupon(couponId);

        // 이미 발급한 수량보다 총수량을 줄이면 발급 이력과 모순된다.
        if (form.getTotalQuantity() < coupon.getIssuedQuantity()) {
            throw new BusinessException(
                    CouponErrorCode.QUANTITY_BELOW_ISSUED
            );
        }

        applyForm(coupon, form);

        if (couponMapper.updateCoupon(coupon) != 1) {
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
}
