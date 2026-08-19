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
 * 설명 : 알림 도메인에 최근 발급된 쿠폰 목록을 제공하는 공개 계약 Service이다.
 * ******************************
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CouponNotificationQueryService {

    private final CouponNotificationMapper couponNotificationMapper;

    /**
     * 특정 시각(since) 이후에 발급된 회원 쿠폰 목록을 조회한다.
     */
    public List<CouponNotificationView> findRecentlyIssuedMemberCoupons(LocalDateTime since) {
        if (since == null) {
            return List.of();
        }
        return couponNotificationMapper.findRecentlyIssuedMemberCoupons(since);
    }
}
