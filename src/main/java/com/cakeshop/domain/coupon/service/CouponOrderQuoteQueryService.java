package com.cakeshop.domain.coupon.service;

import com.cakeshop.domain.coupon.dto.view.CouponOrderQuoteCandidate;
import com.cakeshop.domain.coupon.dto.view.CouponOrderQuoteView;
import com.cakeshop.domain.coupon.mapper.CouponOrderQuoteMapper;
import java.math.BigDecimal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 주문 도메인이 주문서에 표시할 서버 계산 쿠폰 견적을 제공한다. */
@Service
@RequiredArgsConstructor
public class CouponOrderQuoteQueryService {

    private final CouponOrderQuoteMapper couponOrderQuoteMapper;

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
