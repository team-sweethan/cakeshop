package com.cakeshop.domain.notification.service;

import com.cakeshop.domain.coupon.dto.view.CouponNotificationView;
import com.cakeshop.domain.coupon.service.CouponNotificationQueryService;
import com.cakeshop.domain.member.service.MemberNotificationQueryService;
import com.cakeshop.domain.notification.dto.form.NotificationRequest;
import com.cakeshop.domain.notification.entity.DeliveryScope;
import com.cakeshop.domain.notification.entity.NotificationType;
import com.cakeshop.domain.notification.mapper.NotificationMapper;
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
 *        신규 회원가입, 생일, 전체 발급, 관리자 수동 발급 등 사용 시작일이 도래한 모든 유효 쿠폰에 대해 실시간 웹 토스트 및 문자 알림을 발송한다.
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
    private final NotificationMapper notificationMapper;
    private final Clock clock;

    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    private LocalDateTime lastSyncTime;
    private Long lastProcessedMemberCouponId;

    @Async
    @Scheduled(fixedDelay = 3000)
    public void syncCouponNotifications() {
        if (!isRunning.compareAndSet(false, true)) {
            log.trace("이전 쿠폰 알림 동기화 배치가 실행 중이므로 이번 스케줄 조회를 직렬화(스킵)합니다.");
            return;
        }

        try {
            if (lastSyncTime == null) {
                // 재기동 복구: DB notifications 테이블에 기존 쿠폰 알림 기록이 있으면 그 시각부터 복구,
                // 최초 배포 시(알림 이력 없음)에는 현재 시각으로 초기화하여 과거 쿠폰 대량 문자 발송 방어
                LocalDateTime latestNotificationTime = notificationMapper.findLatestCouponNotificationCreatedAt();
                if (latestNotificationTime != null) {
                    lastSyncTime = latestNotificationTime.minusSeconds(10);
                } else {
                    lastSyncTime = LocalDateTime.now(clock);
                }
            }

            int loopCount = 0;
            final int MAX_LOOPS_PER_RUN = 10; // 1회 스케줄당 최대 1,000건까지 페이지 처리
            final int PAGE_SIZE = 100;

            LocalDateTime querySince = lastSyncTime.minusSeconds(10);
            LocalDateTime currentCursorTime = lastSyncTime;
            Long currentCursorId = lastProcessedMemberCouponId;

            while (loopCount < MAX_LOOPS_PER_RUN) {
                loopCount++;

                List<CouponNotificationView> issuedCoupons = couponNotificationQueryService.findRecentlyIssuedMemberCoupons(
                        querySince, currentCursorTime, currentCursorId, PAGE_SIZE
                );

                if (issuedCoupons == null || issuedCoupons.isEmpty()) {
                    break;
                }

                boolean hadErrorInBatch = false;

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
                            currentCursorTime = coupon.issuedAt();
                            currentCursorId = memberCouponId;
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

                        currentCursorTime = coupon.issuedAt();
                        currentCursorId = memberCouponId;

                    } catch (Exception e) {
                        // 특정 행 실패 시 실패 행을 건너뛰어 누락되지 않도록 반복을 중단하고 성공 시점까지만 커서 전진
                        log.error("쿠폰 발급 알림 발송 중 오류 발생 (errorType={}):", e.getClass().getSimpleName());
                        hadErrorInBatch = true;
                        break;
                    }
                }

                if (hadErrorInBatch) {
                    break;
                }

                if (issuedCoupons.size() < PAGE_SIZE) {
                    break;
                }
            }

            this.lastSyncTime = currentCursorTime;
            this.lastProcessedMemberCouponId = currentCursorId;

        } catch (Exception e) {
            log.error("쿠폰 발급 알림 동기화 배치 실행 중 예외 발생 (errorType={}):", e.getClass().getSimpleName());
        } finally {
            isRunning.set(false);
        }
    }
}
