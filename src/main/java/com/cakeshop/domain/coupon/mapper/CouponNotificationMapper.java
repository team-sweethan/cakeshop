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
 * 기능 : 쿠폰 알림용 연동 Mapper 인터페이스
 * 설명 : member_coupons 테이블에서 만료 임박(3일 이내) 유효 쿠폰 목록 조회 및 발송 직전 상태 재검증을 수행한다.
 * ******************************
 */
@Mapper
public interface CouponNotificationMapper {

    /**
     * 현재 사용 가능하고 지정된 일수(days) 이내에 만료 예정인 회원 쿠폰 목록을 복합 커서(lastExpiresAt, lastMemberCouponId)로 조회한다.
     */
    List<CouponNotificationView> findExpiringMemberCoupons(
            @Param("days") int days,
            @Param("lastExpiresAt") LocalDateTime lastExpiresAt,
            @Param("lastMemberCouponId") Long lastMemberCouponId,
            @Param("limit") int limit
    );

    /**
     * 발송 직전 해당 회원 쿠폰이 여전히 사용 가능(AVAILABLE)하고 쿠폰이 활성(ACTIVE) 상태이며 만료되지 않았는지 재검증한다.
     */
    boolean isMemberCouponAvailableAndUnexpired(
            @Param("memberCouponId") Long memberCouponId,
            @Param("expiresAt") LocalDateTime expiresAt
    );
}
