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
 * 설명 : member_coupons 테이블 및 coupons 테이블에서 인덱스 기반으로 발급 쿠폰, 시작 도래 쿠폰, 만료 임박 쿠폰을 조회한다.
 * ******************************
 */
@Mapper
public interface CouponNotificationMapper {

    /**
     * 특정 시점(since) 이후 발급된 유효 회원 쿠폰 목록을 (issued_at, id) 복합 커서로 조회한다. (idx_member_coupons_issued_at_id 인덱스 활용)
     */
    List<CouponNotificationView> findRecentlyIssuedMemberCoupons(
            @Param("since") LocalDateTime since,
            @Param("lastIssuedAt") LocalDateTime lastIssuedAt,
            @Param("lastMemberCouponId") Long lastMemberCouponId,
            @Param("limit") int limit
    );

    /**
     * 특정 시점(since) 이후 사용 시작일(starts_at)이 도래한 유효 회원 쿠폰 목록을 (starts_at, id) 복합 커서로 조회한다. (idx_coupons_status_starts_expires 인덱스 활용)
     */
    List<CouponNotificationView> findRecentlyStartedMemberCoupons(
            @Param("since") LocalDateTime since,
            @Param("lastStartsAt") LocalDateTime lastStartsAt,
            @Param("lastMemberCouponId") Long lastMemberCouponId,
            @Param("limit") int limit
    );

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
