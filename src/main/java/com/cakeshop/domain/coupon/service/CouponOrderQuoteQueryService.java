package com.cakeshop.domain.coupon.service;

import com.cakeshop.domain.coupon.dto.view.CouponOrderQuoteCandidate;
import com.cakeshop.domain.coupon.dto.view.CouponOrderQuoteView;
import com.cakeshop.domain.coupon.mapper.CouponOrderQuoteMapper;
import java.math.BigDecimal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * ******************************
 * 작성자 : 주환
 * 담당자 : 정후
 * 작성일 : 2026-08-12
 * 기능 : 주문서 쿠폰 견적 조회 계약
 * 설명 : 주문 도메인이 쿠폰 테이블에 직접 접근하지 않고, 서버 기준 원금의 적용 가능 쿠폰 견적을 조회하도록 제공한다.
 * ******************************
 */
@Service
@RequiredArgsConstructor
public class CouponOrderQuoteQueryService {

    private final CouponOrderQuoteMapper couponOrderQuoteMapper;

    /**
     * ******************************
     * 작성자 : 주환
     * 담당자 : 정후
     * 작성일 : 2026-08-12
     * 기능 : 0원 초과 결제 쿠폰 견적 조회
     * 설명 : 회원 소유 AVAILABLE 쿠폰 중 적용 후 결제 금액이 0원보다 큰 견적만 주문 화면에 제공한다.
     * ******************************
     */
    @Transactional(readOnly = true)
    public List<CouponOrderQuoteView> getPositiveFinalAmountQuotes(
            long memberId,
            BigDecimal originalAmount
    ) {
        if (memberId <= 0 || originalAmount == null || originalAmount.signum() <= 0) {
            return List.of();
        }

        return couponOrderQuoteMapper.findAvailableQuoteCandidates(memberId, originalAmount)
                .stream()
                .map(candidate -> toQuote(candidate, originalAmount))
                .filter(quote -> quote.finalAmount().signum() > 0)
                .toList();
    }

    private CouponOrderQuoteView toQuote(
            CouponOrderQuoteCandidate candidate,
            BigDecimal originalAmount
    ) {
        BigDecimal discountAmount = CouponDiscountCalculator.calculate(
                candidate.toDiscountPolicy(),
                originalAmount
        );
        return new CouponOrderQuoteView(
                candidate.memberCouponId(),
                candidate.name(),
                discountAmount,
                originalAmount.subtract(discountAmount)
        );
    }
}
