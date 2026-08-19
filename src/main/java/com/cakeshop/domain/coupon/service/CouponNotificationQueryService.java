package com.cakeshop.domain.coupon.service;

import com.cakeshop.domain.coupon.dto.view.CouponNotificationView;
import com.cakeshop.domain.coupon.mapper.CouponNotificationMapper;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * ******************************
 * 작성자 : 김민정
 * 담당자 : 이정후
 * 작성일 : 2026-08-19
 * 기능 : 알림 연동 전용 쿠폰 조회 서비스
 * 설명 : 알림 도메인에 만료 임박(3일 이내) 유효 쿠폰 목록을 복합 커서로 제공하는 공개 계약 Service이다.
 * ******************************
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CouponNotificationQueryService {

    private final CouponNotificationMapper couponNotificationMapper;

    /**
     * 지정된 일수(days) 이내에 만료 예정인 유효 쿠폰 목록을 복합 커서(lastExpiresAt, lastMemberCouponId)로 조회한다.
     */
    public List<CouponNotificationView> findExpiringMemberCoupons(
            int days,
            LocalDateTime lastExpiresAt,
            Long lastMemberCouponId,
            int limit) {
        int targetDays = days > 0 ? days : 3;
        int fetchLimit = limit > 0 ? limit : 100;
        return couponNotificationMapper.findExpiringMemberCoupons(targetDays, lastExpiresAt, lastMemberCouponId, fetchLimit);
    }
}
