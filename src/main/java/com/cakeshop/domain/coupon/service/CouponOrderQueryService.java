package com.cakeshop.domain.coupon.service;

import com.cakeshop.domain.coupon.dto.view.CouponOrderAvailableView;
import java.math.BigDecimal;
import com.cakeshop.domain.coupon.mapper.CouponOrderMapper;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * ******************************
 * 작성자 : 주환(이정후)
 * 담당자 : 이정후
 * 작성일 : 2026-08-10
 * 기능 : 주문서 사용 가능 쿠폰 조회 계약
 * 설명 : order 도메인이 주문 금액을 전달하면, 선택 가능한 쿠폰과 예상 할인 계산에 필요한 최소 정보를 제공한다.
 * ******************************
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
        return couponOrderMapper.findAvailableCouponsForMember(memberId, orderAmount);
    }

}
