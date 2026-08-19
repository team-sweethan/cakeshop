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
 * 설명 : member_coupons 테이블에서 알림 동기화용 최근 발급된 쿠폰 목록을 조회한다.
 * ******************************
 */
@Mapper
public interface CouponNotificationMapper {

    /**
     * 특정 시각(since) 이후에 발급된 회원 쿠폰 목록을 발급일시 오름차순으로 조회한다.
     */
    List<CouponNotificationView> findRecentlyIssuedMemberCoupons(@Param("since") LocalDateTime since);
}
