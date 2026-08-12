package com.cakeshop.domain.coupon.service;

import com.cakeshop.domain.coupon.dto.view.CouponOrderAvailableView;
import java.math.BigDecimal;
import com.cakeshop.domain.coupon.mapper.CouponOrderMapper;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 주문 담당자가 일반·수제 주문서에 표시할 사용 가능 쿠폰을 조회할 때 사용한다.
 */
@Service
@RequiredArgsConstructor
public class CouponOrderQueryService {

    private final CouponOrderMapper couponOrderMapper;

    /** 현재 주문 금액을 만족하는 사용 가능 쿠폰만 주문서 선택 목록으로 제공한다. */
    @Transactional(readOnly = true)
    public List<CouponOrderAvailableView> getAvailableCouponsForMember(
            long memberId, BigDecimal orderAmount
    ) {
        if (orderAmount == null || orderAmount.signum() <= 0) {
            return List.of();
        }
        return couponOrderMapper.findAvailableCouponsForMember(memberId, orderAmount);
    }

}
