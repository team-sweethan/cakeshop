package com.cakeshop.domain.coupon.service;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cakeshop.domain.coupon.dto.view.CustomerCouponQueryView;
import com.cakeshop.domain.coupon.dto.view.CustomerCouponView;
import com.cakeshop.domain.coupon.entity.DiscountType;
import com.cakeshop.domain.coupon.mapper.CouponMemberMapper;
import com.cakeshop.global.common.paging.PageRequest;
import com.cakeshop.global.common.paging.PageResult;

/**
 * ******************************
 * 작성자 : 수민(이정후)
 * 담당자 : 이정후
 * 작성일 : 2026-08-10
 * 기능 : 회원 마이페이지 보유 쿠폰 조회 계약
 * 설명 : member 도메인의 MyPageController가 인증 회원의 사용 가능 쿠폰만 조회할 수 있도록 제공한다.
 * ******************************
 */
@Service
public class CouponMemberQueryService {

    private final CouponMemberMapper couponMemberMapper;

    public CouponMemberQueryService(CouponMemberMapper couponMemberMapper) {
        this.couponMemberMapper = couponMemberMapper;
    }

    /** 인증된 회원의 보유 쿠폰을 발급 이력 기준 최신순으로 페이지 조회한다. */
    @Transactional(readOnly = true)
    public PageResult<CustomerCouponView> getMemberCoupons(long memberId, PageRequest pageRequest) {
        long totalElements = couponMemberMapper.countMemberCoupons(memberId);
        List<CustomerCouponQueryView> coupons = totalElements == 0
                ? List.of()
                : couponMemberMapper.findMemberCoupons(memberId, pageRequest.getSize(), pageRequest.getOffset());

        return new PageResult<>(
                coupons.stream().map(this::toView).toList(),
                pageRequest,
                totalElements
        );
    }

    private CustomerCouponView toView(CustomerCouponQueryView coupon) {
        return new CustomerCouponView(
                coupon.memberCouponId(),
                coupon.name(),
                discountDescription(coupon),
                conditionDescription(coupon.minimumOrderAmount()),
                coupon.status(),
                coupon.issuedAt(),
                coupon.expiresAt()
        );
    }

    private String discountDescription(CustomerCouponQueryView coupon) {
        String value = formatNumber(coupon.discountValue());
        if (coupon.discountType() == DiscountType.FIXED_AMOUNT) {
            return value + "원 할인";
        }

        String description = value + "% 할인";
        if (coupon.maximumDiscountAmount() != null) {
            description += " (최대 " + formatNumber(coupon.maximumDiscountAmount()) + "원)";
        }
        return description;
    }

    private String conditionDescription(BigDecimal minimumOrderAmount) {
        if (minimumOrderAmount == null || minimumOrderAmount.signum() == 0) {
            return "최소 주문 금액 없음";
        }
        return formatNumber(minimumOrderAmount) + "원 이상 구매 시";
    }

    private String formatNumber(BigDecimal amount) {
        return NumberFormat.getNumberInstance(Locale.KOREA).format(amount);
    }
}
