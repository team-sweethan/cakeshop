package com.cakeshop.domain.coupon.mapper;

import com.cakeshop.domain.coupon.dto.view.CouponOrderQuoteCandidate;
import java.math.BigDecimal;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * ******************************
 * 작성자 : 주환
 * 담당자 : 정후
 * 작성일 : 2026-08-12
 * 기능 : 주문서 쿠폰 견적 조회 SQL 계약
 * 설명 : 주문 도메인이 coupon 테이블에 직접 접근하지 않고 회원 쿠폰의 적용 가능 견적 후보를 조회하도록 제공한다.
 * ******************************
 */
@Mapper
public interface CouponOrderQuoteMapper {

    /**
     * ******************************
     * 작성자 : 주환
     * 담당자 : 정후
     * 작성일 : 2026-08-12
     * 기능 : 적용 가능 쿠폰 견적 후보 조회
     * 설명 : AVAILABLE 상태, 사용 기간, 최소 주문 금액을 만족하는 회원 쿠폰 후보만 조회한다.
     * ******************************
     */
    List<CouponOrderQuoteCandidate> findAvailableQuoteCandidates(
            @Param("memberId") long memberId,
            @Param("originalAmount") BigDecimal originalAmount
    );
}
