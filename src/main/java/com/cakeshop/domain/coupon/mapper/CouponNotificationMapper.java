package com.cakeshop.domain.coupon.mapper;

import com.cakeshop.domain.coupon.dto.view.CouponNotificationView;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * ******************************
 * 작성자 : 김민정
 * 담당자 : 이정후
 * 작성일 : 2026-08-19
 * 기능 : 쿠폰 발급 알림용 연동 Mapper 인터페이스
 * 설명 : member_coupons 테이블에서 알림 동기화용 최근 발급된 쿠폰 목록을 복합 커서(issued_at, id)로 조회한다.
 * ******************************
 */
@Mapper
public interface CouponNotificationMapper {

    /**
     * 사용 시작일이 도래한 유효 회원 쿠폰 목록을 복합 커서(lastIssuedAt, lastMemberCouponId) 또는 시각(since) 기준으로 조회한다.
     */
    List<CouponNotificationView> findRecentlyIssuedMemberCoupons(
            @Param("since") LocalDateTime since,
            @Param("lastIssuedAt") LocalDateTime lastIssuedAt,
            @Param("lastMemberCouponId") Long lastMemberCouponId,
            @Param("limit") int limit
    );
}
