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
 * 설명 : 알림 도메인에 사용 시작일이 도래한 유효 쿠폰 목록을 제공하는 공개 계약 Service이다.
 * ******************************
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CouponNotificationQueryService {

    private final CouponNotificationMapper couponNotificationMapper;

    /**
     * 특정 시각(since) 또는 복합 커서 이후에 발급/시작된 유효 쿠폰 목록을 조회한다.
     */
    public List<CouponNotificationView> findRecentlyIssuedMemberCoupons(
            LocalDateTime since,
            LocalDateTime lastIssuedAt,
            Long lastMemberCouponId,
            int limit) {
        if (since == null && lastIssuedAt == null) {
            return List.of();
        }
        int fetchLimit = limit > 0 ? limit : 50;
        return couponNotificationMapper.findRecentlyIssuedMemberCoupons(since, lastIssuedAt, lastMemberCouponId, fetchLimit);
    }
}
