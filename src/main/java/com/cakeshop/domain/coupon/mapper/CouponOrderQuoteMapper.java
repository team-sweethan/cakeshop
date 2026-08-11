package com.cakeshop.domain.coupon.mapper;

import com.cakeshop.domain.coupon.dto.view.CouponOrderQuoteCandidate;
import java.math.BigDecimal;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 주문서 쿠폰 견적 조회 전용 Mapper다. */
@Mapper
public interface CouponOrderQuoteMapper {

    List<CouponOrderQuoteCandidate> findAvailableQuoteCandidates(
            @Param("memberId") long memberId,
            @Param("originalAmount") BigDecimal originalAmount
    );
}
