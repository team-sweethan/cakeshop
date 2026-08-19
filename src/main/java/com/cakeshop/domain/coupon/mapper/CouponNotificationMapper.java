package com.cakeshop.domain.coupon.mapper;

import com.cakeshop.domain.coupon.dto.view.CouponNotificationView;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * ******************************
 * 작성자 : 김민정
 * 담당자 : 이정후
 * 작성일 : 2026-08-19
 * 기능 : 쿠폰 알림용 연동 Mapper 인터페이스
 * 설명 : member_coupons 테이블에서 만료 임박(3일 이내) 유효 쿠폰 목록을 조회한다.
 * ******************************
 */
@Mapper
public interface CouponNotificationMapper {

    /**
     * 현재 사용 가능하고 지정된 일수(days) 이내에 만료 예정인 회원 쿠폰 목록을 만료일 오름차순으로 조회한다.
     */
    List<CouponNotificationView> findExpiringMemberCouponsWithinDays(
            @Param("days") int days,
            @Param("limit") int limit
    );
}
