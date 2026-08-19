package com.cakeshop.domain.notification.service;

import com.cakeshop.domain.coupon.dto.view.CouponNotificationView;
import com.cakeshop.domain.coupon.service.CouponNotificationQueryService;
import com.cakeshop.domain.member.service.MemberNotificationQueryService;
import com.cakeshop.domain.notification.dto.form.NotificationRequest;
import com.cakeshop.domain.notification.entity.DeliveryScope;
import com.cakeshop.domain.notification.entity.NotificationType;
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
 * 담당자 : 김민정
 * 작성일 : 2026-08-19
 * 기능 : 쿠폰 발급 실시간 감지 및 알림 동기화 스케줄러 (알림 도메인 오케스트레이션)
 * 설명 : 타 도메인 코드를 직접 수정하지 않고, 쿠폰 공개 Service로부터 데이터를 조회하여
 *        신규 회원가입, 생일, 전체 발급, 관리자 수동 발급 등 모든 쿠폰 발급에 대해 실시간 웹 토스트 및 문자 알림을 발송한다.
 * ******************************
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationCouponSync {

    private final CouponNotificationQueryService couponNotificationQueryService;
    private final NotificationCouponQueryService notificationCouponQueryService;
    private final MemberNotificationQueryService memberNotificationQueryService;
    private final NotificationService notificationService;
    private final Clock clock;

    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    private LocalDateTime lastSyncTime;

    @Async
    @Scheduled(fixedDelay = 3000)
    public void syncCouponNotifications() {
        if (!isRunning.compareAndSet(false, true)) {
            log.trace("이전 쿠폰 알림 동기화 배치가 실행 중이므로 이번 스케줄 조회를 직렬화(스킵)합니다.");
            return;
        }

        try {
            if (lastSyncTime == null) {
                // 최초 기동 시점: 과거 이미 발급/만료된 쿠폰으로의 대량 문자 오발송을 방지하기 위해 현재 시각으로 초기화
                lastSyncTime = LocalDateTime.now(clock);
            }

            // 트랜잭션 지연 커밋 건의 누락 방지를 위한 10초 겹침 안전 윈도우 적용
            LocalDateTime querySince = lastSyncTime.minusSeconds(10);
            List<CouponNotificationView> issuedCoupons = couponNotificationQueryService.findRecentlyIssuedMemberCoupons(querySince);
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
                    // 발송 직전 시점에도 회원 활성 상태를 재검증
                    if (!memberNotificationQueryService.isMemberActive(memberId)) {
                        continue;
                    }

                    // 이미 완료(SENT/DELIVERED 또는 상한 도달)된 건이 아니면 알림 발송 / 재시도
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
                    // 특정 행 실패 시 실패 행을 건너뛰어 누락되지 않도록 반복을 중단하고 성공 시점까지만 커서 전진
                    log.error("쿠폰 발급 알림 발송 중 오류 발생 (errorType={}):", e.getClass().getSimpleName());
                    break;
                }
            }

            this.lastSyncTime = maxProcessedTime;

        } catch (Exception e) {
            log.error("쿠폰 발급 알림 동기화 배치 실행 중 예외 발생 (errorType={}):", e.getClass().getSimpleName());
        } finally {
            isRunning.set(false);
        }
    }
}
