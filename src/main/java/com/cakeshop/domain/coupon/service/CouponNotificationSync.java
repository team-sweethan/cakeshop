package com.cakeshop.domain.coupon.service;

import com.cakeshop.domain.coupon.dto.view.CouponNotificationView;
import com.cakeshop.domain.notification.dto.form.NotificationRequest;
import com.cakeshop.domain.notification.entity.DeliveryScope;
import com.cakeshop.domain.notification.entity.NotificationType;
import com.cakeshop.domain.notification.service.NotificationCouponQueryService;
import com.cakeshop.domain.notification.service.NotificationService;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * ******************************
 * 작성자 : 김민정
 * 담당자 : 이정후
 * 작성일 : 2026-08-19
 * 기능 : 쿠폰 발급 실시간 감지 및 알림 동기화 스케줄러
 * 설명 : 타 도메인 코드를 직접 수정하지 않고, 서울 시각 Clock, 과거 30일 복구 탐색, AtomicBoolean 직렬화,
 *        비동기 스레드 풀(@Async), 시간 커서 페이징(since) 및 NotificationCouponQueryService 멱등성 검사를 통해
 *        회원가입, 생일, 전체 발급, 관리자 수동 발급 등 모든 쿠폰 발급에 대해 실시간 웹 토스트 및 문자 알림을 발송한다.
 * ******************************
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CouponNotificationSync {

    private final CouponNotificationQueryService couponNotificationQueryService;
    private final NotificationCouponQueryService notificationCouponQueryService;
    private final NotificationService notificationService;
    private final Clock clock;

    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    private LocalDateTime lastSyncTime;

    @Async
    @Scheduled(fixedDelay = 3000)
    public void syncCouponNotifications() {
        if (!isRunning.compareAndSet(false, true)) {
            log.trace("이전 쿠폰 알림 동기화 배치가 여전히 실행 중이므로 이번 스케줄 조회를 직렬화(스킵)합니다.");
            return;
        }

        try {
            if (lastSyncTime == null) {
                // 재시작 시 다운타임 동안의 미처리 알림 완벽 복구를 위해 최근 30일 전부터 탐색
                lastSyncTime = LocalDateTime.now(clock).minusDays(30);
            }

            List<CouponNotificationView> issuedCoupons = couponNotificationQueryService.findRecentlyIssuedMemberCoupons(lastSyncTime);
            if (issuedCoupons == null || issuedCoupons.isEmpty()) {
                return;
            }

            LocalDateTime maxProcessedTime = lastSyncTime;

            for (CouponNotificationView coupon : issuedCoupons) {
                if (coupon.memberCouponId() == null || coupon.memberId() == null) {
                    continue;
                }

                Long memberId = coupon.memberId();
                Long memberCouponId = coupon.memberCouponId();
                String couponName = (coupon.couponName() != null && !coupon.couponName().isBlank()) ? coupon.couponName() : "할인";

                try {
                    // 이미 알림이 전송되었거나 비활성 회원이면 스킵하고 커서 전진
                    if (!notificationCouponQueryService.isCouponNotificationSentOrInactive(memberId, memberCouponId)) {
                        notificationService.makeNotification(NotificationRequest.builder()
                                .receiverId(memberId)
                                .userCouponId(memberCouponId)
                                .type(NotificationType.COUPON)
                                .eventKey(NotificationType.COUPON.name() + ":" + memberId + ":" + memberCouponId)
                                .deliveryScope(DeliveryScope.WEB_AND_SMS)
                                .args(new Object[]{couponName})
                                .build());
                    }

                    if (coupon.issuedAt() != null && coupon.issuedAt().isAfter(maxProcessedTime)) {
                        maxProcessedTime = coupon.issuedAt();
                    }
                } catch (Exception e) {
                    log.error("쿠폰 발급 알림 발송 중 오류 (memberCouponId={}, memberId={}):", memberCouponId, memberId, e);
                }
            }

            this.lastSyncTime = maxProcessedTime;

        } catch (Exception e) {
            log.error("쿠폰 발급 알림 동기화 배치 실행 중 예외 발생:", e);
        } finally {
            isRunning.set(false);
        }
    }
}
